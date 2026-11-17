package com.example.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.AppLanguage
import com.example.data.model.BangladeshLocations
import com.example.data.model.PriceSort
import com.example.data.model.UserRole
import com.example.ui.components.AdBannerSection
import com.example.ui.components.EmptyStateView
import com.example.ui.components.FarmerCard
import com.example.ui.components.InteractiveBangladeshMapDialog
import com.example.ui.components.ProductCard
import com.example.ui.viewmodel.MarketplaceUiState
import com.example.util.LocationHelper
import kotlinx.coroutines.launch

@Composable
fun BrowseScreen(
    uiState: MarketplaceUiState,
    initialTabFarmers: Boolean = false,
    onSearchChange: (String) -> Unit,
    onCategorySelect: (String?) -> Unit,
    onDivisionSelect: (String?) -> Unit,
    onDistrictSelect: (String?) -> Unit,
    onUpazilaSelect: (String?) -> Unit,
    onLocationAutoSelect: (String?, String?, String?) -> Unit = { d, dist, _ ->
        onDivisionSelect(d)
        onDistrictSelect(dist)
    },
    onWholesaleSelect: (Boolean?) -> Unit,
    onSortSelect: (PriceSort) -> Unit,
    onResetFilters: () -> Unit,
    onProductClick: (String) -> Unit,
    onFarmerClick: (String) -> Unit,
    onQuickAddToCart: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lang = uiState.language
    var showFarmersTab by remember(initialTabFarmers) { mutableStateOf(initialTabFarmers) }
    var showMapPicker by remember { mutableStateOf(false) }

    val runGpsFilter: () -> Unit = {
        scope.launch {
            LocationHelper.detectCurrentLocation(context)
                .onSuccess { detected ->
                    onLocationAutoSelect(detected.divisionBn, detected.districtBn, null)
                }
                .onFailure {
                    showMapPicker = true
                }
        }
    }

    val locationPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val granted = grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_FINE_LOCATION] == true
        if (granted) runGpsFilter() else showMapPicker = true
    }

    val activeCategories = remember(uiState.categories) {
        uiState.categories.filter { it.isActive }
    }
    val categoryEmojiMap = remember(uiState.categories) {
        uiState.categories.associate { it.id to it.emoji }
    }
    val divisions = BangladeshLocations.divisions
    val districts = remember(uiState.selectedDivision) {
        if (uiState.selectedDivision == null) BangladeshLocations.allDistricts.map { it.districtBn }
        else BangladeshLocations.districtsForDivision(uiState.selectedDivision).map { it.districtBn }
    }
    val upazilas = remember(uiState.selectedDistrict, uiState.locations) {
        if (uiState.selectedDistrict != null) {
            (BangladeshLocations.upazilasForDistrict(uiState.selectedDistrict) +
                uiState.locations.filter { it.districtBn == uiState.selectedDistrict }.map { it.upazilaBn }).distinct()
        } else {
            emptyList()
        }
    }

    val hasActiveFilter = uiState.searchQuery.isNotBlank() ||
        uiState.selectedDivision != null ||
        uiState.selectedDistrict != null ||
        uiState.selectedUpazila != null ||
        uiState.selectedCategoryId != null ||
        uiState.wholesaleOnly != null ||
        uiState.priceSort != PriceSort.NEWEST

    val isFarmerRole = uiState.activeRole == UserRole.FARMER

    Column(modifier = Modifier.fillMaxSize()) {
        if (isFarmerRole) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .testTag("farmer_market_reference_banner")
            ) {
                Text(
                    text = if (lang == AppLanguage.BENGALI)
                        "📊 কৃষকের বাজার দর রেফারেন্স — বিভিন্ন জেলার ফসলের দাম যাচাই করুন"
                    else
                        "📊 Farmer Market Reference — Compare crop prices across districts",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }
        // Tab Switcher: ফসল vs কৃষক
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            FilterChip(
                selected = !showFarmersTab,
                onClick = { showFarmersTab = false },
                label = {
                    Text(
                        text = if (lang == AppLanguage.BENGALI)
                            "ফসল (${uiState.filteredProducts.size})"
                        else
                            "Crops (${uiState.filteredProducts.size})",
                        fontWeight = FontWeight.Bold
                    )
                },
                modifier = Modifier
                    .weight(1f)
                    .minimumInteractiveComponentSize()
                    .testTag("browse_tab_crops")
            )
            FilterChip(
                selected = showFarmersTab,
                onClick = { showFarmersTab = true },
                label = {
                    Text(
                        text = if (lang == AppLanguage.BENGALI)
                            "কৃষক (${uiState.filteredFarmers.size})"
                        else
                            "Farmers (${uiState.filteredFarmers.size})",
                        fontWeight = FontWeight.Bold
                    )
                },
                modifier = Modifier
                    .weight(1f)
                    .minimumInteractiveComponentSize()
                    .testTag("browse_tab_farmers")
            )
        }

        // Search input
        OutlinedTextField(
            value = uiState.searchQuery,
            onValueChange = onSearchChange,
            placeholder = {
                Text(
                    if (lang == AppLanguage.BENGALI)
                        "ফসল, জেলা, উপজেলা বা কৃষকের নাম লিখুন..."
                    else
                        "Search crops, district, upazila or farmer..."
                )
            },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (uiState.searchQuery.isNotBlank()) {
                    IconButton(onClick = { onSearchChange("") }) {
                        Icon(Icons.Filled.Clear, contentDescription = "মুছুন")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .testTag("browse_search_input")
        )

        Spacer(modifier = Modifier.height(6.dp))

        // Auto GPS / Map Quick Location + Reset Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilledTonalButton(
                onClick = {
                    if (LocationHelper.hasLocationPermission(context)) runGpsFilter()
                    else locationPermLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_COARSE_LOCATION,
                            Manifest.permission.ACCESS_FINE_LOCATION
                        )
                    )
                },
                modifier = Modifier
                    .weight(1f)
                    .testTag("browse_auto_gps_button")
            ) {
                Icon(Icons.Filled.MyLocation, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    if (lang == AppLanguage.BENGALI) "আমার এলাকার ফসল (GPS)" else "Nearby (Auto GPS)",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            OutlinedButton(
                onClick = { showMapPicker = true },
                modifier = Modifier.testTag("browse_open_map_button")
            ) {
                Icon(Icons.Filled.Map, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(if (lang == AppLanguage.BENGALI) "ম্যাপ" else "Map")
            }
            if (hasActiveFilter) {
                IconButton(
                    onClick = onResetFilters,
                    modifier = Modifier.testTag("browse_reset_filters_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.RestartAlt,
                        contentDescription = if (lang == AppLanguage.BENGALI) "ফিল্টার রিসেট" else "Reset filters",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // All Filters as Structured Dropdowns (বিভাগ, জেলা, উপজেলা, সব ক্যাটাগরি, পাইকারি, সাজানো)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Row 1: বিভাগ & জেলা Dropdowns
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val allDivLabel = if (lang == AppLanguage.BENGALI) "সব বিভাগ" else "All Divisions"
                FilterDropdownField(
                    label = if (lang == AppLanguage.BENGALI) "বিভাগ" else "Division",
                    selectedText = uiState.selectedDivision ?: allDivLabel,
                    isActive = uiState.selectedDivision != null,
                    options = listOf(allDivLabel to null) + divisions.map { it to it },
                    onSelect = onDivisionSelect,
                    modifier = Modifier.weight(1f),
                    testTag = "filter_division_dropdown"
                )

                val allDistLabel = if (lang == AppLanguage.BENGALI) "সব জেলা" else "All Districts"
                FilterDropdownField(
                    label = if (lang == AppLanguage.BENGALI) "জেলা" else "District",
                    selectedText = uiState.selectedDistrict ?: allDistLabel,
                    isActive = uiState.selectedDistrict != null,
                    options = listOf(allDistLabel to null) + districts.map { it to it },
                    onSelect = onDistrictSelect,
                    modifier = Modifier.weight(1f),
                    testTag = "filter_district_dropdown"
                )
            }

            // Row 2: উপজেলা & সব ক্যাটাগরি Dropdowns
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val allUpzLabel = if (lang == AppLanguage.BENGALI) "সব উপজেলা" else "All Upazilas"
                FilterDropdownField(
                    label = if (lang == AppLanguage.BENGALI) "উপজেলা" else "Upazila",
                    selectedText = uiState.selectedUpazila ?: allUpzLabel,
                    isActive = uiState.selectedUpazila != null,
                    enabled = upazilas.isNotEmpty(),
                    options = listOf(allUpzLabel to null) + upazilas.map { it to it },
                    onSelect = onUpazilaSelect,
                    modifier = Modifier.weight(1f),
                    testTag = "filter_upazila_dropdown"
                )

                if (!showFarmersTab) {
                    val allCatLabel = if (lang == AppLanguage.BENGALI) "সব ক্যাটাগরি" else "All Categories"
                    val selectedCat = activeCategories.firstOrNull { it.id == uiState.selectedCategoryId }
                    val selectedCatText = if (selectedCat != null) {
                        val catTitle = if (lang == AppLanguage.BENGALI) selectedCat.nameBn else selectedCat.nameEn
                        "${selectedCat.emoji} $catTitle"
                    } else {
                        allCatLabel
                    }
                    FilterDropdownField(
                        label = if (lang == AppLanguage.BENGALI) "সব ক্যাটাগরি" else "Category",
                        selectedText = selectedCatText,
                        isActive = uiState.selectedCategoryId != null,
                        options = listOf(allCatLabel to null) + activeCategories.map { cat ->
                            val catTitle = if (lang == AppLanguage.BENGALI) cat.nameBn else cat.nameEn
                            "${cat.emoji} $catTitle" to cat.id
                        },
                        onSelect = onCategorySelect,
                        modifier = Modifier.weight(1f),
                        testTag = "filter_category_dropdown"
                    )
                }
            }

            // Row 3 (Crops Tab): পাইকারি/খুচরা & দাম অনুযায়ী সাজান Dropdowns
            if (!showFarmersTab) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val allTypeLabel = if (lang == AppLanguage.BENGALI) "সব ধরন (পাইকারি/খুচরা)" else "All Types"
                    val wholesaleLabel = if (lang == AppLanguage.BENGALI) "পাইকারি" else "Wholesale"
                    val retailLabel = if (lang == AppLanguage.BENGALI) "খুচরা" else "Retail"
                    val selectedTypeText = when (uiState.wholesaleOnly) {
                        true -> wholesaleLabel
                        false -> retailLabel
                        null -> allTypeLabel
                    }
                    FilterDropdownField(
                        label = if (lang == AppLanguage.BENGALI) "বিক্রয়ের ধরন" else "Sale Type",
                        selectedText = selectedTypeText,
                        isActive = uiState.wholesaleOnly != null,
                        options = listOf(
                            allTypeLabel to null,
                            wholesaleLabel to true,
                            retailLabel to false
                        ),
                        onSelect = onWholesaleSelect,
                        modifier = Modifier.weight(1f),
                        testTag = "filter_wholesale_dropdown"
                    )

                    val sortNewest = if (lang == AppLanguage.BENGALI) "নতুন আগে" else "Newest First"
                    val sortLow = if (lang == AppLanguage.BENGALI) "দাম: কম → বেশি" else "Price: Low → High"
                    val sortHigh = if (lang == AppLanguage.BENGALI) "দাম: বেশি → কম" else "Price: High → Low"
                    val selectedSortText = when (uiState.priceSort) {
                        PriceSort.NEWEST -> sortNewest
                        PriceSort.PRICE_LOW_HIGH -> sortLow
                        PriceSort.PRICE_HIGH_LOW -> sortHigh
                    }
                    FilterDropdownField(
                        label = if (lang == AppLanguage.BENGALI) "সাজান" else "Sort By",
                        selectedText = selectedSortText,
                        isActive = uiState.priceSort != PriceSort.NEWEST,
                        options = listOf(
                            sortNewest to PriceSort.NEWEST,
                            sortLow to PriceSort.PRICE_LOW_HIGH,
                            sortHigh to PriceSort.PRICE_HIGH_LOW
                        ),
                        onSelect = onSortSelect,
                        modifier = Modifier.weight(1f),
                        testTag = "filter_sort_dropdown"
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (!showFarmersTab) {
            if (uiState.filteredProducts.isEmpty()) {
                EmptyStateView(
                    emoji = "🌾",
                    title = if (lang == AppLanguage.BENGALI) "কোনো ফসল পাওয়া যায়নি" else "No crops found",
                    subtitle = if (lang == AppLanguage.BENGALI)
                        "অনুগ্রহ করে ফিল্টার পরিবর্তন করে পুনরায় খুঁজুন।"
                    else
                        "Try adjusting your search or filters.",
                    actionText = if (lang == AppLanguage.BENGALI) "ফিল্টার রিসেট করুন" else "Reset Filters",
                    onAction = onResetFilters
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 172.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(uiState.filteredProducts, key = { it.id }) { prod ->
                        ProductCard(
                            product = prod,
                            categoryEmoji = categoryEmojiMap[prod.categoryId] ?: "🌾",
                            language = lang,
                            onClick = { onProductClick(prod.id) },
                            onQuickAddToCart = { onQuickAddToCart(prod.id) },
                            showQuickAdd = !isFarmerRole
                        )
                    }
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        AdBannerSection(
                            adConfig = uiState.adConfig,
                            placementTag = "browse_crops",
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        } else {
            if (uiState.filteredFarmers.isEmpty()) {
                EmptyStateView(
                    emoji = "🧑‍🌾",
                    title = if (lang == AppLanguage.BENGALI) "কোনো কৃষক পাওয়া যায়নি" else "No farmers found",
                    subtitle = if (lang == AppLanguage.BENGALI)
                        "অন্য জেলা বা উপজেলা নির্বাচন করে খুঁজুন।"
                    else
                        "Try choosing a different district or upazila.",
                    actionText = if (lang == AppLanguage.BENGALI) "ফিল্টার রিসেট করুন" else "Reset",
                    onAction = onResetFilters
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(uiState.filteredFarmers, key = { it.id }) { farmer ->
                        val count = uiState.products.count { it.farmerId == farmer.id }
                        FarmerCard(
                            farmer = farmer,
                            productCount = count,
                            language = lang,
                            onClick = { onFarmerClick(farmer.id) }
                        )
                    }
                    item {
                        AdBannerSection(
                            adConfig = uiState.adConfig,
                            placementTag = "browse_farmers",
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        }
    }

    if (showMapPicker) {
        InteractiveBangladeshMapDialog(
            initialDistrictBn = uiState.selectedDistrict ?: "কুষ্টিয়া",
            onDismiss = { showMapPicker = false },
            onLocationConfirmed = { detected ->
                onLocationAutoSelect(detected.divisionBn, detected.districtBn, null)
                showMapPicker = false
            }
        )
    }
}

@Composable
private fun <T> FilterDropdownField(
    label: String,
    selectedText: String,
    isActive: Boolean,
    options: List<Pair<String, T>>,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    testTag: String
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = when {
                !enabled -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                isActive -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                else -> MaterialTheme.colorScheme.surface
            },
            border = BorderStroke(
                width = 1.dp,
                color = if (isActive) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outlineVariant
            ),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clickable(enabled = enabled) { expanded = true }
                .testTag(testTag)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isActive) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = selectedText,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                        color = if (enabled) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Icon(
                    imageVector = Icons.Filled.ArrowDropDown,
                    contentDescription = label,
                    tint = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.heightIn(max = 320.dp)
        ) {
            options.forEach { (optionLabel, optionValue) ->
                val isSelected = optionLabel == selectedText
                DropdownMenuItem(
                    text = {
                        Text(
                            text = optionLabel,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface
                        )
                    },
                    trailingIcon = {
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    },
                    onClick = {
                        expanded = false
                        onSelect(optionValue)
                    }
                )
            }
        }
    }
}
