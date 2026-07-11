package com.khataagent.fake

/**
 * Shared name/item pools so the seeded ledger and the simulated turn controller "speak" about
 * the same customers and stock — the demo never mentions someone who doesn't exist yet.
 */
object SeedData {
    val customerNames = listOf(
        "Ramesh Kumar",
        "Sita Devi",
        "Lakshmi Amma",
        "Suresh Reddy",
        "Priya Sharma",
        "Manoj Gupta",
        "Anita Nair",
        "Vijay Singh",
        "Kavita Joshi",
        "Rahul Verma",
        "Deepa Iyer",
        "Arjun Menon",
        "Geeta Patil",
        "Naveen Rao",
        "Shalini Bhat",
    )

    /** item -> Pair(unit, lowWatermark) */
    val inventorySpec = listOf(
        Triple("Rice", "kg", 20.0),
        Triple("Sugar", "kg", 15.0),
        Triple("Wheat Flour (Atta)", "kg", 15.0),
        Triple("Toor Dal", "kg", 10.0),
        Triple("Cooking Oil", "L", 10.0),
        Triple("Salt", "kg", 8.0),
        Triple("Tea Leaves", "kg", 3.0),
        Triple("Milk", "L", 12.0),
        Triple("Soap", "pcs", 20.0),
        Triple("Detergent", "kg", 8.0),
        Triple("Biscuits", "packets", 25.0),
        Triple("Onions", "kg", 15.0),
    )

    val items get() = inventorySpec.map { it.first }
}
