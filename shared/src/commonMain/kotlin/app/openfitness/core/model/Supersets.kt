package app.openfitness.core.model

/**
 * The exercise groups of a day, in order. Each group is a list of slot
 * indices. A group with one slot is a single exercise. A group with two or
 * more slots is a superset: slots next to each other with the same
 * [ExerciseSlot.supersetGroup].
 */
fun TrainingDay.groups(): List<List<Int>> {
    val result = mutableListOf<MutableList<Int>>()
    slots.forEachIndexed { i, slot ->
        val prev = slots.getOrNull(i - 1)
        if (slot.supersetGroup != null && prev?.supersetGroup == slot.supersetGroup) {
            result.last() += i
        } else {
            result += mutableListOf(i)
        }
    }
    return result
}

/** The group that has [slotIndex]. */
fun TrainingDay.groupOf(slotIndex: Int): List<Int> = groups().first { slotIndex in it }

fun TrainingDay.isInSuperset(slotIndex: Int): Boolean = groupOf(slotIndex).size > 1

/**
 * Puts the slot at [slotIndex] and the next slot (with their supersets)
 * in one superset.
 */
fun TrainingDay.linkWithNext(slotIndex: Int): TrainingDay {
    require(slotIndex in 0 until slots.lastIndex) { "No next exercise" }
    val groups = groups()
    val a = groups.indexOfFirst { slotIndex in it }
    val b = groups.indexOfFirst { slotIndex + 1 in it }
    if (a == b) return this
    val members = (groups[a] + groups[b]).toSet()
    val id = (slots.mapNotNull { it.supersetGroup }.maxOrNull() ?: 0) + 1
    return copy(slots = slots.mapIndexed { i, s -> if (i in members) s.copy(supersetGroup = id) else s }).normalized()
}

/** Splits the superset that has [slotIndex] into single exercises. */
fun TrainingDay.split(slotIndex: Int): TrainingDay {
    val members = groupOf(slotIndex).toSet()
    return copy(slots = slots.mapIndexed { i, s -> if (i in members) s.copy(supersetGroup = null) else s }).normalized()
}

/**
 * Gives each superset its own group number, from 1, and clears the group of
 * single exercises. Use this after slots move or are removed.
 */
fun TrainingDay.normalized(): TrainingDay {
    var next = 1
    val ids = IntArray(slots.size)
    groups().forEach { g ->
        val id = if (g.size > 1) next++ else 0
        g.forEach { ids[it] = id }
    }
    return copy(slots = slots.mapIndexed { i, s -> s.copy(supersetGroup = ids[i].takeIf { it > 0 }) })
}
