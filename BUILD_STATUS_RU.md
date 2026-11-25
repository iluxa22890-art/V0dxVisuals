# Сборка V0dxVisuals 2.3.0

Проект настроен под Minecraft 1.21.4, Fabric Loader 0.16.10, Fabric API 0.119.3+1.21.4 и Java 21.

GitHub Actions запускается при push, pull request или вручную через Actions → Build Fabric mod → Run workflow.

Ожидаемый артефакт: `build/libs/vodxvisuals-2.3.0.jar`.

## Важно: исходный ZIP неполный

В архиве `vodxvisuals-2.3.0-src.zip` отсутствуют Java-классы, на которые уже ссылается код:

- `com.vodx.visuals.VodxVisualsClient` (entrypoint из fabric.mod.json)
- `com.vodx.visuals.Fx`
- `com.vodx.visuals.HudRenderer`
- типы `Module`, `Setting` и `Category`, используемые в Modules.java

В исходном `vodxvisuals.mixins.json` также были перечислены `InGameHudMixin` и `EntityMixin`, которых нет в архиве; эти две записи удалены из списка, чтобы не ссылаться на отсутствующие классы.

Поэтому CI-конфигурация и Gradle-проект готовы, но успешная сборка JAR пока не гарантируется: сначала нужно восстановить недостающие Java-классы. Нельзя честно утверждать, что этот исходный набор уже компилируется.
