# OpenFitness

OpenFitness is a hypertrophy training app for Android and iOS. It is built with Kotlin Multiplatform.

## Features (planned)

- Training blocks (mesocycles) of 3 to 8 hard weeks, then one deload week.
- Target reps in reserve (RIR) that go down each week, from 3 to 0.
- Feedback for each muscle: soreness, pump, and workload. Feedback for each exercise: joint pain.
- Automatic changes to sets, weight, and reps from that feedback.
- A built-in exercise library and plan templates.

## Modules

- `shared`: Domain model and progression logic. Pure Kotlin, no platform code.

## How the progression works

1. **Week 0.** Each muscle starts at its start volume. Each exercise gets at least 2 sets.
2. **Sets.** After each session, the feedback for each muscle gives a set change:
   - Still sore, or the work was too much: remove 1 set.
   - Recovered on time and the work was not easy: keep the sets.
   - Fast recovery, low pump, or easy work: add 1 or 2 sets.
   - The weekly total for a muscle does not go above its maximum.
3. **Load.** Double progression. Add reps until the top of the rep range, then add one load step.
4. **Joint pain.** Moderate or severe joint pain lowers the weight by about 10%.
5. **Deload.** Half the sets, half the reps, same weight.

## Run the tests

```
./gradlew :shared:jvmTest
```
