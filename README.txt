# 🎨 MuseumHell

**Sigilo / Atraco cooperativo en un museo procedural**
Diseñado para partidas rápidas donde el equipo entra, roba y escapa evitando guardias y cámaras.

---

## 📜 Género
- 🎯 **FPS de sigilo / heist** con toques de horror ligero

## 👥 Jugadores
- 🧍 **1 jugador** (prototipo)
- 🌐 **2–4 jugadores online** *(en roadmap)*

## 💻 Plataforma
- 🖥 **PC**

---

## 🕵️‍♂️ De qué va

En cada partida exploras un **museo generado proceduralmente**.
Tu objetivo: **robar el botín** repartido por salas **sin activar alarmas**.

- Las **cámaras de seguridad** y un **enemigo que patrulla** reaccionan a tu presencia (visión y luz de la linterna).
- Si te detectan: **alarma → persecución**.
- En cooperativo, el equipo debe coordinarse: distracciones, apoyo con linternas, rutas alternativas y una salida sincronizada.

---

## 🛠 Tecnologías

- ☕ **Java 11**
- 🎮 **JMonkeyEngine 3** (renderizado 3D y escena)
- 🧠 **Bullet Physics**
- ⚙️ **Gradle** (wrapper incluido)
- 💡 **Iluminación dinámica** (Spotlights)
- 🔊 **Audio espacial**

> No necesitas instalar nada raro fuera de Java 11; el wrapper de Gradle ya viene en el repositorio.

---

## 📌 Estado actual

✅ **Prototipo jugable en local** con:
- 🏛 **Generación procedural** de niveles por plantas
- 👮‍♂️ **IA básica** de enemigo (patrulla y persecución)
- 📷 **Cámaras de seguridad** con alarma por sala
- 💰 **Botín aleatorio** + HUD con contador
- 🔦 **Linterna con sombras** y controles FPS *(andar, sprint, agacharse, salto, usar)*

🚧 **Cooperativo online:** en diseño *(ver roadmap)*

---

## 📅 Roadmap

- 🔹 **Co-op online (2–4 jugadores):** lobby, host/cliente, sincronización de estado y física básica
- 🔹 **Roles y gadgets:** ganzúas, inhibidores, distracciones, marcadores de objetivos
- 🔹 **Sigilo avanzado:** detección por luz/sonido, coberturas, superficies ruidosas
- 🔹 **Más enemigos y cámaras:** patrones, barridos, puntos ciegos
- 🔹 **Objetivos y progresión:** contratos, puntuación, dificultad escalable
- 🔹 **Base de operaciones móvil:** una furgoneta como hub entre atracos, donde el equipo podrá equiparse, planificar y seleccionar el próximo destino.
- 🔹 **Opciones y accesibilidad:** remapeo de teclas, FOV, sensibilidad, ayudas visuales
- 🔹 **Demo pública** (itch/Steam) y telemetría básica para balanceo

---

## 🚀 Cómo probar (rápido)

**Requisitos:**
- ☕ Java 17 o superior

**Ejecución:**
```bash
./gradlew run

O abre el proyecto en tu IDE favorito (IntelliJ recomendado) y ejecuta.
