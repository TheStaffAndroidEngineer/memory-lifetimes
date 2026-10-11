package staff.android.memory.core

data class Product(val id: String, val title: String, val price: String, val description: String)
val products = listOf(
    Product("camera", "Camera", "₹12,999", "Keep the moments that matter. A compact camera with a familiar feel and room for your next story."),
    Product("tea", "Tea", "₹450", "A slower start, a warmer cup. Whole-leaf tea for the small pauses in your everyday routine."),
    Product("bag", "Day bag", "₹2,490", "Light enough for the everyday, spacious enough for the unexpected. Your essentials, together."),
    Product("lamp", "Reading lamp", "₹1,990", "A quiet pool of light for one more chapter. Made for a bedside table or a favourite corner."),
    Product("mug", "Everyday mug", "₹690", "A simple shape with a comfortable handle. At home with your first coffee and your last cup of tea."),
    Product("headphones", "Headphones", "₹5,990", "A little space for what you love to hear. Settle into a playlist, a call or a long journey.")
)
