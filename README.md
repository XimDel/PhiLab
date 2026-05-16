# PhiLab 

**PhiLab** es una aplicación móvil nativa para Android que convierte la cámara de tu celular en un laboratorio portátil de física. Usando visión por computadora en tiempo real, captura el movimiento rectilíneo de un objeto y calcula automáticamente sus parámetros cinemáticos: posición, velocidad, aceleración y distancia recorrida.

---

## Características principales

- **Detección de objetos en tiempo real** usando modelos TFLite (`SSD MobileNet` y `EfficientDet`)
- **Tracking con Filtro de Kalman** para suavizado y predicción de trayectorias
- **Calibración con marcador ArUco** para conversión precisa de píxeles a centímetros
- **Análisis cinemático** de Movimiento Rectilíneo Uniforme (MRU) y Uniformemente Acelerado (MRUA)
- **Generación de gráficos** de posición, velocidad y aceleración en función del tiempo
- **Exportación de reportes** en formato PDF y CSV
- Arquitectura **MVVM** limpia y escalable

---

## ¿Cómo funciona?

1. Se coloca un **marcador ArUco de tamaño conocido** en la escena para calibrar la escala (px → cm).
2. La cámara captura el movimiento de un objeto (por ejemplo, una pelota).
3. Los modelos de detección identifican el objeto frame a frame.
4. El Filtro de Kalman estabiliza la trayectoria y predice posiciones intermedias.
5. Se calculan posición, velocidad y aceleración a partir de los datos de tracking.
6. Los resultados se visualizan en gráficos y pueden exportarse como PDF o CSV.

---

## Stack tecnológico

| Componente | Tecnología |
|---|---|
| Lenguaje principal | Kotlin |
| Arquitectura | MVVM |
| Visión por computadora | OpenCV (C++ via NDK/JNI) |
| Detección de objetos | TensorFlow Lite (SSD MobileNet, EfficientDet) |
| Tracking | Filtro de Kalman |
| Calibración espacial | Marcadores ArUco |
| Build system | Gradle (Kotlin DSL) |
| Plataforma | Android |

---

## Parámetros cinemáticos calculados

- **Posición** `x(t)` — trayectoria del objeto en el tiempo
- **Distancia recorrida** — longitud total del camino en cm
- **Desplazamiento** — diferencia vectorial entre posición inicial y final en cm
- **Velocidad** `v(t)` — velocidad instantánea y promedio
- **Aceleración** `a(t)` — variación de velocidad en el tiempo
- **Clasificación del movimiento** — MRU (aceleración ≈ 0) o MRUA (aceleración constante)

---

## Estructura del proyecto

```
PhiLab/
├── app/src/main/java/com/example/philab/
│   ├── core/                  # Lógica de bajo nivel
│   │   ├── calibration/       # Calibración de escala px → cm
│   │   ├── camera/            # Acceso y configuración de la cámara
│   │   ├── detection/         # Inferencia con modelos TFLite
│   │   └── measurement/       # Cálculo de parámetros cinemáticos
│   ├── data/                  # Capa de datos (MVVM)
│   │   ├── aruco/             # Procesamiento de marcadores ArUco
│   │   ├── local/
│   │   │   ├── dao/           # Data Access Objects (Room)
│   │   │   ├── database/      # Configuración de la base de datos
│   │   │   └── entity/        # Entidades persistidas
│   │   └── repository/        # Repositorios
│   ├── domain/                # Casos de uso y modelos de negocio
│   │   ├── aruco/
│   │   ├── experiment/        # Lógica de un experimento cinemático
│   │   ├── export/            # Exportación a PDF y CSV
│   │   ├── model/             # Modelos de dominio
│   │   └── pipeline/          # Pipeline de procesamiento de video
│   └── ui/                    # Capa de presentación (MVVM)
│       ├── home/              # Pantalla principal
│       ├── history/           # Historial de experimentos
│       ├── lab/
│       │   ├── arucogenerator/ # Generador de marcadores ArUco
│       │   └── experiment/    # Nucleo de la app
│       │       ├── camera/    # Captura en tiempo real
│       │       └── tips/      # Guía de uso del laboratorio
│       │   └── faqs/          # Preguntas frecuentes
│       │   └── menu/
│       ├── navigation/        # Navegación entre pantallas
│       ├── theme/             # Tema visual de la app
│       └── theory/            # Contenido teórico de física
│           ├── article/
│           └── module/
├── opencv/                    # Módulo OpenCV (NDK/JNI)
├── build.gradle.kts
└── settings.gradle.kts
```

---

## Instalación

### Opción 1 — APK directo
Descarga el APK desde la sección de [Releases](https://github.com/XimDel/PhiLab/releases/tag/v1.0) e instálalo en tu dispositivo Android (requiere habilitar *fuentes desconocidas*).

### Opción 2 — Compilar desde el código fuente

**Requisitos:**
- Android Studio Hedgehog o superior
- Android SDK API 24+
- NDK

```bash
git clone https://github.com/XimDel/PhiLab.git
cd PhiLab
# Abre el proyecto en Android Studio y ejecuta en tu dispositivo
```

---

## Exportación de datos

Los reportes generados incluyen:

- Tabla de datos crudos (tiempo, posición, velocidad, aceleración)
- Gráficas de `x(t)`, `v(t)` y `a(t)`
- Resumen del tipo de movimiento detectado (MRU / MRUA)

Formatos disponibles: **PDF** · **CSV**
