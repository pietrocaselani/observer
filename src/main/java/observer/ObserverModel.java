package observer;

import com.sun.source.util.Trees;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.JCMethodDecl;
import com.sun.tools.javac.tree.JCTree.JCVariableDecl;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.tools.Diagnostic;

/**
 * Created by Pietro Caselani
 * On 11/17/14
 * Property-Observer
 */
final class ObserverModel {
	private final ProcessingEnvironment mEnvironment;
	private Element mFieldElement, mClassElement;
	private Observer mObserver;
	private String mPropertyName, mSetterName;
	private JCVariableDecl mFieldDecl;

	public ObserverModel(Element element, ProcessingEnvironment environment) {
		mEnvironment = environment;
		setFieldElement(element);
	}

	public Element getFieldElement() {
		return mFieldElement;
	}

	public void setFieldElement(Element fieldElement) {
		mFieldElement = fieldElement;

		mClassElement = fieldElement.getEnclosingElement();

		mObserver = fieldElement.getAnnotation(Observer.class);

		mFieldDecl = (JCVariableDecl) Trees.instance(mEnvironment).getTree(fieldElement);

		mPropertyName = mObserver.value();

		if (mPropertyName.length() == 0) {
			final String propertyName = mFieldElement.getSimpleName().toString().substring(1);
			mPropertyName = String.valueOf(Character.toLowerCase(propertyName.charAt(0))) + propertyName.substring(1);
		}

		mSetterName = "set" + mFieldElement.getSimpleName().toString().substring(1);
	}

	public JCVariableDecl getFieldDecl() {
		return mFieldDecl;
	}

	public Element getClassElement() {
		return mClassElement;
	}

	public Observer getObserver() {
		return mObserver;
	}

	public String getPropertyName() {
		return mPropertyName;
	}

	public String getSetterName() {
		return mSetterName;
	}
}