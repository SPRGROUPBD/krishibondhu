package com.example.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
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
import com.example.data.model.equalsBn
import com.example.data.model.normalizeBn
import com.example.ui.components.AdBannerSection
import com.example.ui.components.EmptyStateView
import com.example.ui.components.FarmerCard
import com.example.ui.components.InteractiveBangladeshMapDialog
import com.example.ui.components.ProductBrowseRowCard
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
    onUnionSelect: (String?) -> Unit = {},
    onLocationAutoSelect: (String?, String?, String?) -> Unit = { d, dist, _ ->
        onDivisionSelect(d)
        onDistrictSelect(dist)
    },
    onUseMyLocation: (() -> Unit)? = null,
    onWholesaleSelect: (Boolean?) -> Unit,
    onSortSelect: (PriceSort) -> Unit,
    onResetFilters: () -> Unit,
    onRefreshSync: () -> Unit = {},
    onProductClick: (String) -> Unit,
    onFarmerClick: (String) -> Unit,
    onQuickAddToCart: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lang = uiState.language
    var showFarmersTab by remember(initialTabFarmers) { mutableStateOf(initialTabFarmers) }
    var showMapPicker by remember { mutableStateOf(false) }
    var filtersExpanded by remember { mutableStateOf(false) }

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
    val divisions = remember {
        BangladeshLocations.divisions.map { it.normalizeBn() }.distinct()
    }
    val districts = remember(uiState.selectedDivision, uiState.locations, uiState.products, uiState.farmers) {
        val base = if (uiState.selectedDivision == null) {
            BangladeshLocations.allDistricts.map { it.districtBn }
        } else {
            BangladeshLocations.districtsForDivision(uiState.selectedDivision).map { it.districtBn }
        }
        val extra = (uiState.locations.filter {
            uiState.selectedDivision == null || it.divisionBn.equalsBn(uiState.selectedDivision)
        }.map { it.districtBn } +
            uiState.products.filter {
                it.status.equals("ACTIVE", ignoreCase = true) &&
                    (uiState.selectedDivision == null || it.division.equalsBn(uiState.selectedDivision))
            }.map { it.district } +
            uiState.farmers.filter {
                uiState.selectedDivision == null || it.division.equalsBn(uiState.selectedDivision)
            }.map { it.district })
        (base + extra)
            .map { it.normalizeBn() }
            .filter { it.isNotBlank() }
            .distinct()
    }
    val upazilas = remember(
        uiState.selectedDivision,
        uiState.selectedDistrict,
        uiState.locations,
        uiState.products,
        uiState.farmers
    ) {
        val fromStatic = when {
            uiState.selectedDistrict != null ->
                BangladeshLocations.upazilasForDistrict(uiState.selectedDistrict)
            uiState.selectedDivision != null ->
                BangladeshLocations.districtsForDivision(uiState.selectedDivision).flatMap { it.upazilas }
            else -> emptyList()
        }
        val fromLocations = uiState.locations.filter { loc ->
            (uiState.selectedDivision == null || loc.divisionBn.equalsBn(uiState.selectedDivision)) &&
                (uiState.selectedDistrict == null || loc.districtBn.equalsBn(uiState.selectedDistrict))
        }.map { it.upazilaBn }
        val fromFarmers = uiState.farmers.filter { f ->
            (uiState.selectedDivision == null || f.division.equalsBn(uiState.selectedDivision)) &&
                (uiState.selectedDistrict == null || f.district.equalsBn(uiState.selectedDistrict))
        }.map { it.upazila }
        val fromProducts = uiState.products.filter { p ->
            p.status.equals("ACTIVE", ignoreCase = true) &&
                (uiState.selectedDivision == null || p.division.equalsBn(uiState.selectedDivision)) &&
                (uiState.selectedDistrict == null || p.district.equalsBn(uiState.selectedDistrict))
        }.map { it.upazila }
        (fromStatic + fromLocations + fromFarmers + fromProducts)
            .map { it.normalizeBn() }
            .filter { it.isNotBlank() }
            .distinct()
            .let { list -> if (uiState.selectedDistrict != null) list else list.sorted() }
    }
    val unions = remember(
        uiState.selectedDivision,
        uiState.selectedDistrict,
        uiState.selectedUpazila,
        uiState.locations,
        uiState.farmers,
        uiState.products
    ) {
        val fromLocations = uiState.locations.filter { loc ->
            (uiState.selectedDivision == null || loc.divisionBn.equalsBn(uiState.selectedDivision)) &&
                (uiState.selectedDistrict == null || loc.districtBn.equalsBn(uiState.selectedDistrict)) &&
                (uiState.selectedUpazila == null || loc.upazilaBn.equalsBn(uiState.selectedUpazila))
        }.map { it.unionBn }
        val fromFarmers = uiState.farmers.filter { f ->
            (uiState.selectedDivision == null || f.division.equalsBn(uiState.selectedDivision)) &&
                (uiState.selectedDistrict == null || f.district.equalsBn(uiState.selectedDistrict)) &&
                (uiState.selectedUpazila == null || f.upazila.equalsBn(uiState.selectedUpazila))
        }.map { it.unionName }
        val fromProducts = uiState.products.filter { p ->
            p.status.equals("ACTIVE", ignoreCase = true) &&
                (uiState.selectedDivision == null || p.division.equalsBn(uiState.selectedDivision)) &&
                (uiState.selectedDistrict == null || p.district.equalsBn(uiState.selectedDistrict)) &&
                (uiState.selectedUpazila == null || p.upazila.equalsBn(uiState.selectedUpazila))
        }.map { it.unionName }
        (fromLocations + fromFarmers + fromProducts)
            .map { it.normalizeBn() }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
    }

    val activeFilterCount = listOfNotNull(
        uiState.selectedDivision,
        uiState.selectedDistrict,
        uiState.selectedUpazila,
        uiState.selectedUnion,
        uiState.selectedCategoryId,
        uiState.priceSort.takeIf { it != PriceSort.NEWEST }
    ).size
    val hasActiveFilter = uiState.searchQuery.isNotBlank() || activeFilterCount > 0
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
                .padding(horizontal = 16.dp, vertical = 6.dp),
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

        // Search input + manual sync refresh button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = onSearchChange,
                placeholder = {
                    Text(
                        text = if (lang == AppLanguage.BENGALI)
                            "ফসল, জেলা, উপজেলা, ইউনিয়ন বা কৃষকের নাম..."
                        else
                            "Search crops, district, upazila, union or farmer...",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
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
                    .weight(1f)
                    .testTag("browse_search_input")
            )

            FilledTonalIconButton(
                onClick = onRefreshSync,
                enabled = !uiState.isSyncing,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("browse_refresh_button")
            ) {
                if (uiState.isSyncing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = if (lang == AppLanguage.BENGALI) "ডাটা রিফ্রেশ করুন" else "Refresh data"
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Compact Action Bar: Filter Toggle, GPS, My Area, Map, Reset
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterChip(
                selected = filtersExpanded || activeFilterCount > 0,
                onClick = { filtersExpanded = !filtersExpanded },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Tune,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                },
                label = {
                    val text = if (lang == AppLanguage.BENGALI) {
                        if (activeFilterCount > 0) "ফিল্টার ($activeFilterCount)" else "ফিল্টার"
                    } else {
                        if (activeFilterCount > 0) "Filters ($activeFilterCount)" else "Filters"
                    }
                    Text(text, fontWeight = FontWeight.SemiBold, maxLines = 1)
                },
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("browse_toggle_filters_button")
            )

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
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                modifier = Modifier
                    .heightIn(min = 40.dp)
                    .testTag("browse_auto_gps_button")
            ) {
                Icon(Icons.Filled.MyLocation, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    if (lang == AppLanguage.BENGALI) "GPS এলাকা" else "Auto GPS",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (uiState.activeUser != null && onUseMyLocation != null) {
                OutlinedButton(
                    onClick = onUseMyLocation,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    modifier = Modifier
                        .heightIn(min = 40.dp)
                        .testTag("browse_my_location_button")
                ) {
                    Text(if (lang == AppLanguage.BENGALI) "আমার এলাকা" else "My Area", maxLines = 1)
                }
            }

            OutlinedButton(
                onClick = { showMapPicker = true },
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                modifier = Modifier
                    .heightIn(min = 40.dp)
                    .testTag("browse_open_map_button")
            ) {
                Icon(Icons.Filled.Map, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(if (lang == AppLanguage.BENGALI) "ম্যাপ" else "Map", maxLines = 1)
            }

            if (hasActiveFilter) {
                OutlinedButton(
                    onClick = onResetFilters,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                    modifier = Modifier
                        .heightIn(min = 40.dp)
                        .testTag("browse_reset_filters_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.RestartAlt,
                        contentDescription = if (lang == AppLanguage.BENGALI) "ফিল্টার রিসেট" else "Reset filters",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (lang == AppLanguage.BENGALI) "রিসেট" else "Reset", maxLines = 1)
                }
            }
        }

        // Active filter chips strip when panel is collapsed
        if (hasActiveFilter && !filtersExpanded) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (uiState.searchQuery.isNotBlank()) {
                    InputChip(
                        selected = true,
                        onClick = { onSearchChange("") },
                        label = { Text("🔍 ${uiState.searchQuery}", maxLines = 1) },
                        trailingIcon = { Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(14.dp)) }
                    )
                }
                uiState.selectedDivision?.let { div ->
                    InputChip(
                        selected = true,
                        onClick = { onDivisionSelect(null) },
                        label = { Text(div, maxLines = 1) },
                        trailingIcon = { Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(14.dp)) }
                    )
                }
                uiState.selectedDistrict?.let { dist ->
                    InputChip(
                        selected = true,
                        onClick = { onDistrictSelect(null) },
                        label = { Text(dist, maxLines = 1) },
                        trailingIcon = { Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(14.dp)) }
                    )
                }
                uiState.selectedUpazila?.let { upz ->
                    InputChip(
                        selected = true,
                        onClick = { onUpazilaSelect(null) },
                        label = { Text(upz, maxLines = 1) },
                        trailingIcon = { Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(14.dp)) }
                    )
                }
                uiState.selectedUnion?.let { uni ->
                    InputChip(
                        selected = true,
                        onClick = { onUnionSelect(null) },
                        label = { Text(uni, maxLines = 1) },
                        trailingIcon = { Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(14.dp)) }
                    )
                }
                uiState.selectedCategoryId?.let { catId ->
                    val cat = activeCategories.firstOrNull { it.id == catId }
                    if (cat != null) {
                        InputChip(
                            selected = true,
                            onClick = { onCategorySelect(null) },
                            label = {
                                Text(
                                    "${cat.emoji} ${if (lang == AppLanguage.BENGALI) cat.nameBn else cat.nameEn}",
                                    maxLines = 1
                                )
                            },
                            trailingIcon = { Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(14.dp)) }
                        )
                    }
                }
                if (uiState.priceSort != PriceSort.NEWEST) {
                    InputChip(
                        selected = true,
                        onClick = { onSortSelect(PriceSort.NEWEST) },
                        label = {
                            Text(
                                when (uiState.priceSort) {
                                    PriceSort.PRICE_LOW_HIGH -> if (lang == AppLanguage.BENGALI) "কম দাম" else "Low→High"
                                    PriceSort.PRICE_HIGH_LOW -> if (lang == AppLanguage.BENGALI) "বেশি দাম" else "High→Low"
                                    PriceSort.NEWEST -> ""
                                },
                                maxLines = 1
                            )
                        },
                        trailingIcon = { Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(14.dp)) }
                    )
                }
            }
        }

        // Collapsible Structured Dropdowns Panel
        AnimatedVisibility(visible = filtersExpanded) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
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

                    // Row 2: উপজেলা & ইউনিয়ন Dropdowns
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

                        val allUnionLabel = if (lang == AppLanguage.BENGALI) "সব ইউনিয়ন" else "All Unions"
                        FilterDropdownField(
                            label = if (lang == AppLanguage.BENGALI) "ইউনিয়ন" else "Union",
                            selectedText = uiState.selectedUnion ?: allUnionLabel,
                            isActive = uiState.selectedUnion != null,
                            enabled = unions.isNotEmpty(),
                            options = listOf(allUnionLabel to null) + unions.map { it to it },
                            onSelect = onUnionSelect,
                            modifier = Modifier.weight(1f),
                            testTag = "filter_union_dropdown"
                        )
                    }

                    // Row 3 (Crops Tab): ক্যাটাগরি, পাইকারি/খুচরা & দাম অনুযায়ী সাজান Dropdowns
                    if (!showFarmersTab) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val allCatLabel = if (lang == AppLanguage.BENGALI) "সব ক্যাটাগরি" else "All Categories"
                            val selectedCat = activeCategories.firstOrNull { it.id == uiState.selectedCategoryId }
                            val selectedCatText = if (selectedCat != null) {
                                val catTitle = if (lang == AppLanguage.BENGALI) selectedCat.nameBn else selectedCat.nameEn
                                "${selectedCat.emoji} $catTitle"
                            } else {
                                allCatLabel
                            }
                            FilterDropdownField(
                                label = if (lang == AppLanguage.BENGALI) "ক্যাটাগরি" else "Category",
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

                            val sortNewest = if (lang == AppLanguage.BENGALI) "নতুন" else "Newest"
                            val sortLow = if (lang == AppLanguage.BENGALI) "কম দাম" else "Low→High"
                            val sortHigh = if (lang == AppLanguage.BENGALI) "বেশি দাম" else "High→Low"
                            val selectedSortText = when (uiState.priceSort) {
                                PriceSort.NEWEST -> sortNewest
                                PriceSort.PRICE_LOW_HIGH -> sortLow
                                PriceSort.PRICE_HIGH_LOW -> sortHigh
                            }
                            FilterDropdownField(
                                label = if (lang == AppLanguage.BENGALI) "সাজান" else "Sort",
                                selectedText = selectedSortText,
                                isActive = uiState.priceSort != PriceSort.NEWEST,
                                options = listOf(
                                    sortNewest to PriceSort.NEWEST,
                                    sortLow to PriceSort.PRICE_LOW_HIGH,
                                    sortHigh to PriceSort.PRICE_HIGH_LOW
                                ),
                                onSelect = onSortSelect,
                                modifier = Modifier.weight(0.85f),
                                testTag = "filter_sort_dropdown"
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val useListLayout = maxWidth < 540.dp

            if (!showFarmersTab) {
                if (uiState.filteredProducts.isEmpty()) {
                    when {
                        uiState.isSyncing -> {
                            SyncingStateCard(
                                message = if (lang == AppLanguage.BENGALI)
                                    "অনলাইন মার্কেট থেকে ফসলের তথ্য লোড হচ্ছে..."
                                else
                                    "Loading crops from online market..."
                            )
                        }
                        uiState.products.isEmpty() -> {
                            EmptyStateView(
                                emoji = if (!uiState.isOnline) "📡" else "🌾",
                                title = if (lang == AppLanguage.BENGALI)
                                    "অনলাইন থেকে ফসল লোড করা যায়নি"
                                else
                                    "Could not load crops",
                                subtitle = uiState.syncError ?: if (lang == AppLanguage.BENGALI)
                                    "ইন্টারনেট সংযোগ নিশ্চিত করে আবার চেষ্টা করুন।"
                                else
                                    "Check your internet connection and reload.",
                                actionText = if (lang == AppLanguage.BENGALI) "পুনরায় লোড করুন" else "Reload",
                                onAction = onRefreshSync
                            )
                        }
                        else -> {
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
                        }
                    }
                } else if (useListLayout) {
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(uiState.filteredProducts, key = { it.id }) { prod ->
                            ProductBrowseRowCard(
                                product = prod,
                                categoryEmoji = categoryEmojiMap[prod.categoryId] ?: "🌾",
                                language = lang,
                                onClick = { onProductClick(prod.id) },
                                onQuickAddToCart = { onQuickAddToCart(prod.id) },
                                showQuickAdd = !isFarmerRole
                            )
                        }
                        item {
                            AdBannerSection(
                                adConfig = uiState.adConfig,
                                placementTag = "browse_crops",
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 210.dp),
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
                    when {
                        uiState.isSyncing -> {
                            SyncingStateCard(
                                message = if (lang == AppLanguage.BENGALI)
                                    "অনলাইন থেকে কৃষকদের তালিকা লোড হচ্ছে..."
                                else
                                    "Loading farmers from online market..."
                            )
                        }
                        uiState.farmers.isEmpty() -> {
                            EmptyStateView(
                                emoji = if (!uiState.isOnline) "📡" else "🧑‍🌾",
                                title = if (lang == AppLanguage.BENGALI)
                                    "কৃষকদের তালিকা লোড করা যায়নি"
                                else
                                    "Could not load farmers",
                                subtitle = uiState.syncError ?: if (lang == AppLanguage.BENGALI)
                                    "ইন্টারনেট সংযোগ নিশ্চিত করে আবার চেষ্টা করুন।"
                                else
                                    "Check your internet connection and reload.",
                                actionText = if (lang == AppLanguage.BENGALI) "পুনরায় লোড করুন" else "Reload",
                                onAction = onRefreshSync
                            )
                        }
                        else -> {
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
                        }
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(uiState.filteredFarmers, key = { it.id }) { farmer ->
                            val count = uiState.products.count { it.farmerId == farmer.id && it.status == "ACTIVE" }
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
private fun SyncingStateCard(message: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CircularProgressIndicator()
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
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
                val isSelected = optionLabel.equalsBn(selectedText)
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

