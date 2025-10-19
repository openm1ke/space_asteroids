-injars build/classes
-outjars build/preverified

# J2ME API-стабы — не вшиваем внутрь
-libraryjars lib/cldcapi11.jar
-libraryjars lib/midpapi20.jar

# Включает режим Micro Edition (preverify)
-microedition

# Без усложнений
-dontobfuscate
-dontoptimize
-dontwarn

# Сохраняем ваш MIDlet-класс
-keep class space.SpaceMidlet {
    public <init>();
    public void *(...);
}

