# Правила R8 для релизной сборки.
#
# Hilt, Compose и Room приносят свои правила через consumer-rules —
# здесь только то, что специфично для приложения.

# Классы маршрутов навигации сериализуются kotlinx.serialization.
-keepattributes *Annotation*, InnerClasses, Signature
-dontnote kotlinx.serialization.**

-keepclassmembers class **$$serializer {
    *** descriptor;
}
-keepclasseswithmembers class ** {
    @kotlinx.serialization.Serializable <fields>;
}
