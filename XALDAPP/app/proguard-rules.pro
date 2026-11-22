#proguard-rules.pro

# SQLCipher (cifrado de la base local): el AAR trae sus reglas, esto es un respaldo por si R8
# elimina clases usadas desde JNI.
-keep class net.zetetic.database.** { *; }
