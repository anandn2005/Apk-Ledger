package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.decode.SvgDecoder
import coil.request.ImageRequest

data class VectorIconItem(
    val key: String,
    val displayName: String,
    val tags: List<String>,
    val fallbackVector: ImageVector
) {
    val svgUrl: String
        get() = "https://cdn.jsdelivr.net/npm/lucide-static@latest/icons/$key.svg"
}

/**
 * Curated registry of open vector icons across commerce, grocery, agriculture,
 * retail, and accounting with searchable tags.
 */
object VectorIconRegistry {

    val icons: List<VectorIconItem> = listOf(
        // Curated Kirana & Indian General Trade Categories
        VectorIconItem("sparkles", "Soap & Surf", listOf("soap", "surf", "detergent", "wash", "clean", "rin", "wheel", "tide", "cleaning", "bath", "shampoo"), Icons.Default.CleanHands),
        VectorIconItem("flower-2", "Masala / Spices", listOf("masala", "spices", "mirch", "haldi", "dhaniya", "garam masala", "chili", "pepper", "turmeric", "cumin", "jeera", "herbs"), Icons.Default.Spa),
        VectorIconItem("utensils", "Namkeen / Snacks", listOf("namkeen", "snack", "snacks", "chips", "kurkure", "mixture", "bhujia", "sev", "farsan", "bites"), Icons.Default.Fastfood),
        VectorIconItem("cookie", "Biscuit / Bakery", listOf("biscuit", "biscuits", "cookie", "cookies", "bakery", "toast", "rusk", "bread", "cake", "parle", "snack"), Icons.Default.Cookie),
        VectorIconItem("flame", "Tobacco / Bidi", listOf("tobacco", "bidi", "cigarette", "cigarettes", "pan masala", "gutkha", "smoke", "match", "matchbox", "fire"), Icons.Default.SmokingRooms),
        VectorIconItem("shopping-bag", "Daily Use / Kirana", listOf("daily use", "daily", "general", "household", "basket", "home", "kirana", "provision", "grocery", "pack"), Icons.Default.ShoppingBag),
        VectorIconItem("droplet", "Oil & Ghee", listOf("oil", "ghee", "tel", "refined", "mustard oil", "mustard", "soyabean", "dalda", "butter", "cooking oil", "liquid"), Icons.Default.Opacity),
        VectorIconItem("apple", "Dry Fruit & Nuts", listOf("dry fruit", "dry fruits", "kaju", "badam", "kishmish", "almond", "cashew", "walnut", "raisin", "nuts", "pista", "fruit"), Icons.Default.Eco),
        VectorIconItem("wheat", "Salt / Grains / Atta", listOf("salt", "namak", "grains", "atta", "flour", "wheat", "rice", "dal", "pulses", "sugar", "cheeni", "grain", "sack", "harvest"), Icons.Default.Grass),
        VectorIconItem("cup-soda", "Disposal / Plastic", listOf("disposal", "plastic", "cup", "paper cup", "dona", "pattal", "pouch", "packing", "takeout", "bag", "cold drink", "beverage"), Icons.Default.LocalBar),
        
        // General Commerce & Mandi Categories
        VectorIconItem("shopping-cart", "Cart / Retail", listOf("cart", "trolley", "shopping", "store", "buy"), Icons.Default.ShoppingCart),
        VectorIconItem("package", "Package / Box", listOf("box", "package", "carton", "parcel", "stock"), Icons.Default.Inventory2),
        VectorIconItem("boxes", "Boxes / Bulk Inventory", listOf("box", "boxes", "crate", "inventory", "bulk"), Icons.Default.AllInbox),
        VectorIconItem("corn", "Corn / Maize / Feed", listOf("grain", "corn", "crop", "agriculture", "feed"), Icons.Default.Eco),
        VectorIconItem("banknote", "Cash / Banknote", listOf("cash", "money", "rupee", "currency", "bill", "notes"), Icons.Default.AttachMoney),
        VectorIconItem("wallet", "Wallet / Khata", listOf("cash", "wallet", "money", "balance", "pay"), Icons.Default.AccountBalanceWallet),
        VectorIconItem("coins", "Coins / Change", listOf("cash", "coins", "change", "money", "small cash"), Icons.Default.MonetizationOn),
        VectorIconItem("receipt", "Receipt / Billing", listOf("receipt", "bill", "invoice", "slip", "tax"), Icons.Default.ReceiptLong),
        VectorIconItem("tag", "Tag / Price Rate", listOf("tag", "price", "rate", "label", "cost"), Icons.Default.Sell),
        VectorIconItem("scale", "Scale / Weight", listOf("scale", "weight", "weigh", "kg", "gram", "measure", "quintal"), Icons.Default.Scale),
        VectorIconItem("coffee", "Coffee / Tea / Chai", listOf("tea", "coffee", "chai", "beverage", "drink"), Icons.Default.Coffee),
        VectorIconItem("croissant", "Bakery / Bread / Rusk", listOf("bread", "bakery", "pastry", "flour", "rusk"), Icons.Default.BreakfastDining),
        VectorIconItem("milk", "Milk / Dairy / Curd", listOf("milk", "dairy", "butter", "curd", "paneer", "dahi"), Icons.Default.WaterDrop),
        VectorIconItem("truck", "Transport / Delivery", listOf("truck", "transport", "delivery", "vehicle", "logistics", "mandi"), Icons.Default.LocalShipping),
        VectorIconItem("store", "Store / Shop / Mandi", listOf("store", "shop", "bazaar", "mandi", "market", "dukan"), Icons.Default.Storefront),
        VectorIconItem("archive", "Stock / Godown", listOf("archive", "box", "storage", "godown", "warehouse"), Icons.Default.Archive),
        VectorIconItem("layers", "General Category", listOf("category", "layers", "catalog", "others", "group"), Icons.Default.Category),
        VectorIconItem("badge-percent", "Discount / Scheme", listOf("discount", "percent", "offer", "margin", "scheme"), Icons.Default.Discount),
        VectorIconItem("shield-check", "Quality Assured", listOf("shield", "verified", "quality", "assured", "safe"), Icons.Default.Verified),
        VectorIconItem("gem", "Premium Goods", listOf("gem", "luxury", "diamond", "jewel", "precious"), Icons.Default.Diamond),
        VectorIconItem("fuel", "Fuel / Petrol", listOf("fuel", "petrol", "diesel", "gas", "pump"), Icons.Default.LocalGasStation),
        VectorIconItem("plug", "Electrical Goods", listOf("plug", "electric", "appliance", "wire", "cable"), Icons.Default.Power),
        VectorIconItem("wrench", "Hardware / Tools", listOf("hardware", "tools", "wrench", "repair", "fittings"), Icons.Default.Build),
        VectorIconItem("shirt", "Textile / Clothes", listOf("shirt", "clothes", "fabric", "textile", "garment"), Icons.Default.Checkroom),
        VectorIconItem("book-open", "Stationery / Khata Book", listOf("book", "khata", "register", "stationery", "paper"), Icons.Default.MenuBook),
        VectorIconItem("pill", "Pharma / Medicine", listOf("medicine", "pharma", "pill", "tablet", "health"), Icons.Default.Medication),
        VectorIconItem("landmark", "Mandi / Yard / Bank", listOf("bank", "mandi", "government", "yard", "market"), Icons.Default.AccountBalance),
        VectorIconItem("database", "Ledger Data", listOf("database", "records", "data", "system", "sync"), Icons.Default.Storage)
    )

    fun search(query: String): List<VectorIconItem> {
        val trimmed = query.trim().lowercase()
        if (trimmed.isEmpty()) return icons
        return icons.filter { item ->
            item.displayName.lowercase().contains(trimmed) ||
            item.key.lowercase().contains(trimmed) ||
            item.tags.any { it.contains(trimmed) || trimmed.contains(it) }
        }
    }

    fun findByKey(key: String?): VectorIconItem? {
        if (key.isNullOrBlank()) return null
        return icons.find { it.key.equals(key.trim(), ignoreCase = true) }
    }

    fun inferIconForCategory(category: String): VectorIconItem {
        val lower = category.lowercase().trim()
        val found = icons.find { icon ->
            icon.tags.any { tag -> lower.contains(tag) || tag.contains(lower) } || 
            lower.contains(icon.displayName.lowercase())
        }
        return found ?: icons.first { it.key == "layers" }
    }
}

/**
 * Universal online vector / SVG icon display component.
 * Loads the SVG from CDN via Coil's SvgDecoder, with instant fallback to standard Material Icons.
 */
@Composable
fun VectorIconView(
    iconKey: String?,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    tint: Color = MaterialTheme.colorScheme.primary,
    fallbackVector: ImageVector = Icons.Default.Category
) {
    val context = LocalContext.current
    val item = remember(iconKey) { VectorIconRegistry.findByKey(iconKey) }
    val effectiveFallback = item?.fallbackVector ?: fallbackVector

    if (item != null) {
        val imageRequest = remember(item.svgUrl) {
            ImageRequest.Builder(context)
                .data(item.svgUrl)
                .decoderFactory(SvgDecoder.Factory())
                .crossfade(true)
                .build()
        }

        AsyncImage(
            model = imageRequest,
            contentDescription = contentDescription ?: item.displayName,
            modifier = modifier,
            colorFilter = ColorFilter.tint(tint),
            error = androidx.compose.ui.graphics.vector.rememberVectorPainter(effectiveFallback),
            placeholder = androidx.compose.ui.graphics.vector.rememberVectorPainter(effectiveFallback)
        )
    } else {
        Icon(
            imageVector = effectiveFallback,
            contentDescription = contentDescription,
            tint = tint,
            modifier = modifier
        )
    }
}

/**
 * Integrated Searchable Category Icon Picker Dialog.
 * Allows searching by query ("cart", "grain", "box", "cash", etc.),
 * selecting from a live responsive grid, and attaching the vector key & CDN URL
 * to the category document for Firestore synchronization.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryIconPickerDialog(
    initialCategoryName: String = "",
    initialIconKey: String = "",
    title: String? = null,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)? = null,
    onConfirm: (categoryName: String, iconKey: String, iconUrl: String) -> Unit
) {
    var categoryName by remember { mutableStateOf(initialCategoryName) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedIconKey by remember {
        mutableStateOf(
            if (initialIconKey.isNotBlank()) initialIconKey
            else VectorIconRegistry.inferIconForCategory(initialCategoryName).key
        )
    }

    val filteredIcons = remember(searchQuery) {
        VectorIconRegistry.search(searchQuery)
    }

    val selectedItem = remember(selectedIconKey) {
        VectorIconRegistry.findByKey(selectedIconKey) ?: VectorIconRegistry.icons.first()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        var animateIn by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            animateIn = true
        }

        val scale by androidx.compose.animation.core.animateFloatAsState(
            targetValue = if (animateIn) 1.0f else 0.92f,
            animationSpec = androidx.compose.animation.core.spring(
                dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
                stiffness = 360f
            ),
            label = "iconPickerModalScale"
        )
        val alpha by androidx.compose.animation.core.animateFloatAsState(
            targetValue = if (animateIn) 1.0f else 0.0f,
            animationSpec = androidx.compose.animation.core.tween(180),
            label = "iconPickerModalAlpha"
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = if (animateIn) 0.55f else 0f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                ),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .fillMaxHeight(0.88f)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        this.alpha = alpha
                    }
                    .clip(RoundedCornerShape(24.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    ),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                shadowElevation = 16.dp
            ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title ?: if (initialCategoryName.isBlank()) "New Category & Icon" else "Edit Category & Icon",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (onDelete != null) {
                            IconButton(
                                onClick = onDelete,
                                modifier = Modifier.testTag("delete_category_button")
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Delete Category",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.testTag("close_icon_picker_dialog")
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Category Name Field
                OutlinedTextField(
                    value = categoryName,
                    onValueChange = { 
                        categoryName = it
                        if (initialIconKey.isBlank()) {
                            selectedIconKey = VectorIconRegistry.inferIconForCategory(it).key
                        }
                    },
                    label = { Text("Category Name") },
                    placeholder = { Text("e.g. Soap & Surf, Masala, Namkeen, Biscuit") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("category_name_input"),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Active Selection Preview Chip
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary),
                                contentAlignment = Alignment.Center
                            ) {
                                VectorIconView(
                                    iconKey = selectedIconKey,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = categoryName.ifBlank { "Category Preview" },
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Vector: ${selectedItem.displayName}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        SuggestionChip(
                            onClick = {},
                            label = { Text("Selected", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                            shape = CircleShape
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Real-Time Search Bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search: soap, masala, snacks, biscuit, oil...") },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(18.dp))
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("icon_search_input"),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Icon Grid
                Text(
                    text = "CURATED GROCERY & TRADE ICONS (${filteredIcons.size})",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    letterSpacing = 0.6.sp
                )

                Spacer(modifier = Modifier.height(6.dp))

                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 64.dp),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    items(filteredIcons, key = { it.key }) { iconItem ->
                        val isSelected = iconItem.key == selectedIconKey

                        Surface(
                            onClick = { selectedIconKey = iconItem.key },
                            shape = RoundedCornerShape(14.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                            modifier = Modifier
                                .aspectRatio(1f)
                                .testTag("icon_option_${iconItem.key}")
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                VectorIconView(
                                    iconKey = iconItem.key,
                                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = iconItem.displayName,
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Cancel")
                    }

                    Button(
                        onClick = {
                            val trimmedName = categoryName.trim()
                            if (trimmedName.isNotBlank()) {
                                onConfirm(trimmedName, selectedItem.key, selectedItem.svgUrl)
                            }
                        },
                        enabled = categoryName.isNotBlank(),
                        modifier = Modifier
                            .weight(1.5f)
                            .testTag("confirm_category_icon_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (onDelete != null) "Update Category" else "Attach & Save", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
}

