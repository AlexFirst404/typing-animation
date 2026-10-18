package dev.typinganimation.mc;

/** Animation state of a vanilla normal or hanging sign editor, without changing its text/input behavior. */
public interface SignStateHolder {
    SignAnimation typinganimation$signAnimation();
    String typinganimation$signMessage(int line);
    int typinganimation$signLine();
}
