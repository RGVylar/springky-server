# Sprinky Server

Este repositorio contiene el backend de un juego multijugador en tiempo real inspirado en Jackbox Party Pack. Está desarrollado con Java y Spring Boot.

## Descripción general

Sprinky es un juego de completar frases en rondas. Un jugador crea una sala y comparte el código con sus amigos. Se soportan hasta 8 jugadores. Cuando la sala está preparada, el anfitrión inicia la partida y se inician rondas con tres fases: PROMPT, SUBMITTING y SCORING.

- **PROMPT:** Se muestra una frase con un hueco por completar.
- **SUBMITTING:** Cada jugador envía su respuesta.
- **SCORING:** Se reparten puntos y se avanza a la siguiente ronda.

El juego soporta dos modos: **AUTO**, donde la transición entre fases y rondas es automática tras un tiempo definido, y **MANUAL**, donde el anfitrión controla el avance.

## Estado actual

Esta rama contiene la primera implementación funcional del servidor:

- Creación y unión a salas con un código de 4 caracteres.
- Gestión de tokens para anfitrión y jugadores.
- Inicio de partida y avance por fases mediante un método programado que comprueba los deadlines.
- Envío de respuestas de los jugadores y puntuación simple (una respuesta no vacía otorga un punto).
- Modo AUTO y MANUAL con lógica para programar el cambio de ronda o esperar al anfitrión.
- Endpoints REST para crear sala, unirse, iniciar partida, enviar respuesta, avanzar de ronda manualmente y cambiar de modo.
- Broadcast del estado de la sala a los clientes suscritos mediante WebSocket (`/topic/rooms/{code}`).

## Cómo ejecutar

1. **Requisitos:** Java 17+, Maven.
2. Clonar el repositorio y situarse en la rama `feature/base-game-structure`.
3. Ejecutar `./mvnw spring-boot:run` (o `mvnw.cmd` en Windows).
4. El servidor se inicia en `http://localhost:8080`.
5. Para probar WebSocket, los clientes deben suscribirse a `/topic/rooms/{code}`.

## Roadmap y próximas tareas

Para alcanzar una versión lista para publicar en Steam necesitamos abordar las siguientes tareas:

1. **Implementar prompts dinámicos:** reemplazar el prompt fijo por un generador o lista de frases.
2. **Persistencia y reconexión:** almacenar el estado de la sala en base de datos y permitir que los jugadores se reconecten.
3. **Mejorar la puntuación:** añadir votaciones entre jugadores (fase VOTING) y asignar puntuaciones variables.
4. **Configuración de tiempos:** permitir configurar las duraciones de PROMPT, SUBMITTING y SCORING por sala.
5. **Interfaz cliente:** desarrollar un cliente web que se conecte por WebSocket y muestre el estado en tiempo real.
6. **Gestión de errores y validación:** validar entradas y mejorar los mensajes de error.
7. **Despliegue y distribución:** preparar Dockerfile y pipeline de despliegue para subir el juego a Steam.
8. **Mejoras de seguridad:** usar tokens JWT y proteger los endpoints sensibles.
9. **Pruebas unitarias e integración:** añadir una suite de tests para asegurar la estabilidad del backend.

## Licencia

Este proyecto se distribuye bajo licencia propietaria.
