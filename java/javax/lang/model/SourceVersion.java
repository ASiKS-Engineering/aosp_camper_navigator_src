package javax.lang.model;

public enum SourceVersion {
    RELEASE_0, RELEASE_1, RELEASE_2, RELEASE_3, RELEASE_4, RELEASE_5, RELEASE_6, RELEASE_7, RELEASE_8, RELEASE_9, RELEASE_10, RELEASE_11;

    public static boolean isIdentifier(CharSequence name) {
        String s = name.toString();
        if (s.isEmpty()) return false;
        if (!Character.isJavaIdentifierStart(s.charAt(0))) return false;
        for (int i = 1; i < s.length(); i++) {
            if (!Character.isJavaIdentifierPart(s.charAt(i))) return false;
        }
        return true;
    }

    public static boolean isKeyword(CharSequence s) {
        return false;
    }
}
