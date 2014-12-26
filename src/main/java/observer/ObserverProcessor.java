package observer;

import com.sun.source.util.Trees;
import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.model.JavacElements;
import com.sun.tools.javac.processing.JavacProcessingEnvironment;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.JCAssign;
import com.sun.tools.javac.tree.JCTree.JCClassDecl;
import com.sun.tools.javac.tree.JCTree.JCExpression;
import com.sun.tools.javac.tree.JCTree.JCExpressionStatement;
import com.sun.tools.javac.tree.JCTree.JCLiteral;
import com.sun.tools.javac.tree.JCTree.JCMethodDecl;
import com.sun.tools.javac.tree.JCTree.JCStatement;
import com.sun.tools.javac.tree.JCTree.JCVariableDecl;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.Name;
import com.sun.tools.javac.util.Name.Table;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import java.util.ArrayList;
import java.util.Set;

import static com.sun.tools.javac.util.Name.fromString;
import static javax.tools.Diagnostic.Kind.ERROR;
import static javax.tools.Diagnostic.Kind.NOTE;

/**
 * Created by Pietro Caselani
 * On 11/17/14
 * Property-Observer
 */
@SupportedAnnotationTypes("observer.Observer")
public final class ObserverProcessor extends AbstractProcessor {
	//region Fields
	private TreeMaker mTreeMaker;
	//endregion

	//region Processor
	@Override public synchronized void init(ProcessingEnvironment processingEnvironment) {
		super.init(processingEnvironment);

		mTreeMaker = TreeMaker.instance(((JavacProcessingEnvironment) processingEnvironment).getContext());
	}

	@Override public boolean process(Set<? extends TypeElement> set, RoundEnvironment roundEnvironment) {
		final ArrayList<ObserverModel> observerModels = parseObserverAnnotations(roundEnvironment);

		try {
			for (final ObserverModel observerModel : observerModels) {
				wrapSetters(observerModel);
			}
		} catch (Exception e) {
			String fullClassName = Thread.currentThread().getStackTrace()[2].getClassName();
			String className = fullClassName.substring(fullClassName.lastIndexOf(".") + 1);
			String methodName = Thread.currentThread().getStackTrace()[2].getMethodName();
			int lineNumber = Thread.currentThread().getStackTrace()[2].getLineNumber();

			error(null, className + "." + methodName + "():" + lineNumber + ":\nMensagem:" + e.getLocalizedMessage());

			for (StackTraceElement stackTraceElement : e.getStackTrace()) {
				error(null, stackTraceElement.toString());
			}

			error(null, e.toString());
		}

		return false;
	}
	//endregion

	//region Private
	private void wrapSetters(ObserverModel observerModel) {
		final Trees trees = Trees.instance(processingEnv);

		final JCClassDecl classTree = (JCClassDecl) trees.getTree(observerModel.getClassElement());

		final JCMethodDecl setterDecl = findSetter(classTree, observerModel.getSetterName());

		if (setterDecl == null) {
			error(observerModel.getFieldElement(), "Field %s does not have setter", observerModel.getFieldElement().getSimpleName().toString());
			return;
		}

		final Element propertyChangeSupportElement = findPropertyChangeSupportElement(observerModel.getClassElement());
		if (propertyChangeSupportElement == null) {
			error(observerModel.getClassElement(), "Class %s needs a field of type java.beans.PropertyChangeSupport", classTree.name.toString());
			return;
		}

		final JCVariableDecl propertyChangeSupportTree = (JCVariableDecl) trees.getTree(propertyChangeSupportElement);

		List<JCStatement> statements = List.nil();

		final JCExpression type = setterDecl.params.get(0).vartype;

		final Name oldValueVarName = getName("oldValue");

		final JCVariableDecl oldValueVar = mTreeMaker.VarDef(mTreeMaker.Modifiers(Flags.FINAL), oldValueVarName, type, mTreeMaker.Ident(observerModel.getFieldDecl()));
		statements = statements.append(oldValueVar);
		statements = statements.appendList(setterDecl.body.stats);

		JCExpression firePropertyChangeExpression = generateDotExpression(propertyChangeSupportTree.name.toString(), "firePropertyChange");

		final JCLiteral propertyLiteral = mTreeMaker.Literal(observerModel.getPropertyName());
		final JCExpression oldValueLiteral = mTreeMaker.Ident(oldValueVarName);

		final List<JCExpression> args = List.from(new JCExpression[]{propertyLiteral, oldValueLiteral, mTreeMaker.Ident(setterDecl.params.get(0).name)});

		firePropertyChangeExpression = mTreeMaker.Apply(List.<JCExpression>nil(), firePropertyChangeExpression, args);

		statements = statements.append(mTreeMaker.Exec(firePropertyChangeExpression));

		setterDecl.body.stats = statements;
	}

	private JCExpression generateDotExpression(String... expression) {
		if (expression.length == 0) return null;

		String arg1 = expression[0];

		JCExpression jcExpression = mTreeMaker.Ident(getName(arg1));

		for (int i = 1; i < expression.length; i++) {
			jcExpression = mTreeMaker.Select(jcExpression, getName(expression[i]));
		}

		return jcExpression;
	}

	private Element findPropertyChangeSupportElement(Element classElement) {
		final Elements elementUtils = processingEnv.getElementUtils();
		final Types typeUtils = processingEnv.getTypeUtils();

		TypeMirror configuratorType = elementUtils.getTypeElement("java.beans.PropertyChangeSupport").asType();

		for (final Element childElement : classElement.getEnclosedElements()) {
			if (childElement.getKind().isField() && typeUtils.isSameType(childElement.asType(), configuratorType)) {
				return childElement;
			}
		}

		return null;
	}

	private void error(Element element, String message, Object... args) {
		processingEnv.getMessager().printMessage(ERROR, String.format(message, args), element);
	}

	private Name getName(String s) {
		return JavacElements.instance(((JavacProcessingEnvironment) processingEnv).getContext()).getName(s);
	}

	private JCMethodDecl findSetter(JCClassDecl classDecl, String methodName) {
		for (final JCTree def : classDecl.defs) {
			if (def instanceof JCMethodDecl) {
				final JCMethodDecl methodDecl = (JCMethodDecl) def;
				if (methodDecl.name.toString().equals(methodName)) {
					return methodDecl;
				}
			}
		}
		return null;
	}

	private ArrayList<ObserverModel> parseObserverAnnotations(RoundEnvironment env) {
		final Set<? extends Element> observerElements = env.getElementsAnnotatedWith(Observer.class);

		ArrayList<ObserverModel> observerModels = new ArrayList<ObserverModel>(observerElements.size());

		for (final Element observerElement : observerElements) {
			observerModels.add(new ObserverModel(observerElement, processingEnv));
		}

		return observerModels;
	}
	//endregion
}