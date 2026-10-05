# Mundo Mascotas 🐾 — v1.0

Juego de mascotas en español, inspirado en la referencia proporcionada. Ilustraciones SVG propias: esta versión reproduce las funciones y la estructura visual, con un estilo gráfico simplificado. Sin anuncios, pagos reales, cuentas ni servidor. Funciona sin internet.

## Incluye
- Perro, gato, hámster y pollito, nombres editables y necesidades individuales.
- Alimentación, baño, cariño, alegría, nivel y experiencia.
- Cuatro minijuegos: saltos (3 vidas), monedas (30 segundos), tesoros (8 intentos) y memoria (6 parejas).
- Tienda con monedas del juego; accesorios y mundo de 16 terrenos.
- Regalo diario, sonido, controles táctiles y progreso local automático.
- Proyecto Android nativo con WebView y compilación GitHub Actions.

## Subir a GitHub (paso a paso)
1. Crea un repositorio llamado **MundoMascotas**.
2. Extrae este ZIP. Sube el **contenido** de `MundoMascotas`, no otra carpeta contenedora. En la raíz deben quedar `web`, `android`, `tests` y `.github`.
3. Confirma que `.github/workflows/android.yml` está incluido. GitHub puede ocultar carpetas que empiezan por punto; si falta, crea el archivo con **Add file → Create new file** y copia su contenido.
4. Abre **Actions → Build Mundo Mascotas Android**. El primer proceso puede tardar varios minutos.
5. Al terminar en verde, abre el proceso y descarga **MundoMascotas-APK**, sección **Artifacts**.
6. Descomprime el artefacto e instala `app-debug.apk` en tu Android, autorizando la instalación desde esa fuente.

## Probar en computador
Con Python 3 instalado, desde la raíz: `python3 -m http.server 8080 --directory web`. Abre `http://localhost:8080`. También puedes abrir `web/index.html`, aunque el guardado con archivos locales depende del navegador.

## Controles
Toca a la mascota para acariciarla. En saltos, toca la pantalla o usa espacio. En monedas, arrastra la cesta o usa las flechas. En construcción, compra y selecciona una decoración y toca el terreno; la escoba retira objetos. El botón Atrás de Android vuelve al menú y sale desde la portada.

## Guardado y monedas
Empiezas con 250 monedas. Alimentar cuesta 5. Los demás cuidados son gratuitos. El progreso depende de los datos locales de la app: borrar datos o desinstalar puede eliminarlo. No hay sincronización entre teléfonos. Regalo diario y reloj de necesidades usan la hora del dispositivo; no constituyen una economía protegida contra cambios del reloj.

## Android y publicación
Paquete: `cl.negociospyme.blockpets`. Java 17, Gradle 8.11.1, Android Gradle Plugin 8.9.2, SDK 35, mínimo Android 6. El workflow descarga herramientas de compilación desde sus repositorios oficiales. El APK es para pruebas. El AAB generado **no está firmado**: antes de publicar se debe configurar una clave de lanzamiento, firma, ficha de tienda y requisitos vigentes de Play Console. No subir claves al repositorio. No está integrado AdMob.

## Archivos
- `web/app.js`: pantallas y minijuegos.
- `web/engine.js`: reglas, tienda y progreso.
- `web/style.css`: diseño adaptable.
- `android/`: aplicación contenedora Android.
- `.github/workflows/android.yml`: APK y AAB automáticos.
- `tests/rules.test.cjs`: pruebas de economía y necesidades (`node tests/rules.test.cjs`).

Hecho por NegociosPyme · by Juan Alarcón.
