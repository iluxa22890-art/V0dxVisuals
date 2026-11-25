# Vodx Visuals 2.3.0 — что внутри

Исходники в Yarn-именах (как ваш MaceImpact.java), Minecraft 1.21.4.
Положите папку `src/` поверх вашего проекта (заменить/добавить файлы).

## Заменить
- `java/com/vodx/visuals/Modules.java`   (новые модули + переделанная KillAura)
- `java/com/vodx/visuals/KillAura.java`  (медленнее и плавнее)
- `java/com/vodx/visuals/Effects.java`   (подключает Vfx и MaceImpact; MaceImpact.init() из VodxVisualsClient больше вызывать не нужно)
- `resources/vodxvisuals.mixins.json`, `resources/fabric.mod.json`

## Добавить
- `java/com/vodx/visuals/Vfx.java`        киллэффект (Souls / Shatter / Lightning), косметика, дуга удара, трещины земли
- `java/com/vodx/visuals/MaceImpact.java` ваш файл, доработанный (трещины + осколки, сила зависит от падения)
- `java/com/vodx/visuals/Sky.java`        цвета неба
- `java/com/vodx/visuals/SwingAnim.java`  анимация замаха
- `java/com/vodx/visuals/mixin/{HeldItemRendererMixin,LivingEntityMixin,ClientWorldMixin,BackgroundRendererMixin}.java`
- `resources/assets/**`                    текстуры, модели, определения предметов

Если раньше вставляли MaceImpact из чата — удалите старый MaceImpact.java и строку `MaceImpact.init()` в VodxVisualsClient, и кусок MACE_IMPACT из Modules (он уже есть в новом).
Все миксины с `require = 0`: если что-то в 1.21.4 не совпало, мод не вылетит, а просто эта фича не сработает.
