package com.derycode.deryaccount.catalog

/**
 * BusinessCatalog — the predefined Ugandan product catalogs.
 * 20 business categories with 1240+ common items, typical market
 * prices, and SUB-CATEGORIES so stock and sales screens can filter fast
 * (e.g. Pharmacy → "Pain & Fever", "Antibiotics", …).
 * Powers the Quick Stock Setup wizard so a new shop can stock its shelves
 * in minutes instead of typing items one by one.
 * NOTE: this file is data, not logic — edit prices/items only.
 */
object BusinessCatalog {

    data class CatalogItem(
        val name: String,
        val unit: String,
        val price: Double,
        val subcategory: String = ""
    )

    val CATEGORIES = listOf(
        "General Shop", "Supermarket", "Hardware", "Pharmacy / Drug Shop", "Agro-Vet & Farm", "Butchery", "Salon & Cosmetics", "Stationery / Bookshop", "Bar & Restaurant", "Mobile Money & Airtime", "Produce & Grains", "Electronics & Accessories", "Fruits & Vegetables", "Poultry & Eggs", "Fish Business", "Bakery & Confectionery", "Wholesale & Distribution", "Tailoring & Fashion", "School Canteen / Tuck Shop", "Transport & Boda Services"
    )

    // Robust parser: new lines are "Sub|Name|unit|price", legacy lines are
    // "Name|unit|price" (kept backward compatible). Bad lines are skipped —
    // a typo in data must never take down the shop.
    private fun parse(block: String): List<CatalogItem> = block.trimIndent().lines()
        .filter { it.isNotBlank() }
        .map { it.trim().removePrefix("|") }
        .mapNotNull { line ->
            val p = line.split("|").map { it.trim() }
            when {
                p.size >= 4 -> {
                    val price = p[3].toDoubleOrNull() ?: return@mapNotNull null
                    CatalogItem(p[1], p[2].ifBlank { "pcs" }, price, subcategory = p[0])
                }
                p.size == 3 -> {
                    val price = p[2].toDoubleOrNull() ?: return@mapNotNull null
                    CatalogItem(p[0], p[1].ifBlank { "pcs" }, price)
                }
                else -> null
            }
        }

    /** Distinct sub-categories of a category (for the filter chips). */
    fun subcategoriesFor(category: String): List<String> =
        itemsFor(category).map { it.subcategory }.filter { it.isNotBlank() }.distinct()

    fun itemsFor(category: String): List<CatalogItem> = when (category) {
        "General Shop" -> parse("""
|Grains & Breakfast|Sugar 1kg|kg|5000
|Grains & Breakfast|Sugar 2kg|pack|9800
|Grains & Breakfast|Sugar 500g|pack|2600
|Grains & Breakfast|Rice 1kg Kaiso|kg|4500
|Grains & Breakfast|Rice 1kg Super|kg|6500
|Grains & Breakfast|Rice 2kg|pack|9000
|Grains & Breakfast|Rice 5kg|pack|28000
|Grains & Breakfast|Posho 1kg|kg|3500
|Grains & Breakfast|Posho 5kg|pack|16000
|Grains & Breakfast|Millet flour 1kg|kg|4000
|Grains & Breakfast|Wheat flour 1kg|kg|5500
|Grains & Breakfast|Wheat flour 2kg|pack|10500
|Cooking & Oils|Cooking oil 500ml|btl|4500
|Cooking & Oils|Cooking oil 1L|btl|8500
|Cooking & Oils|Cooking oil 3L|jerrican|24000
|Cooking & Oils|Cooking oil 5L|jerrican|39000
|Cooking & Oils|Salt 500g|pack|1000
|Cooking & Oils|Salt 1kg|pack|2000
|Bread & Bakery|Bread|loaf|4500
|Bread & Bakery|Bread sliced|loaf|5000
|Bread & Bakery|Buns|pc|500
|Cooking & Oils|Baking powder 100g|tin|1500
|Cooking & Oils|Yeast 100g|pkt|3000
|Beans & Nuts|Beans 1kg|kg|5000
|Beans & Nuts|Beans 5kg|tin|24000
|Beans & Nuts|Groundnuts 1kg|kg|7000
|Beans & Nuts|Groundnut paste 500g|tin|9000
|Beans & Nuts|Simsim paste 500g|tin|9000
|Beans & Nuts|Gnuts roasted 100g|pkt|1500
|Dairy & Eggs|Milk 500ml|pkt|2000
|Dairy & Eggs|Milk 1L|pkt|3800
|Dairy & Eggs|Milk powder 400g|tin|14000
|Beverages|Tea leaves 100g|tin|3000
|Beverages|Tea leaves 250g|tin|6500
|Beverages|Coffee 100g|tin|3500
|Beverages|Coffee 250g|tin|8000
|Beverages|Cocoa 100g|tin|3000
|Beverages|Cocoa 250g|tin|6500
|Beverages|Milo 200g|tin|5500
|Beverages|Ovaltine 200g|tin|5500
|Cooking & Oils|Ghee 250g|tin|8000
|Cooking & Oils|Honey 500g|bottle|15000
|Cooking & Oils|Curry powder 50g|pack|1000
|Cooking & Oils|Curry powder 100g|pack|2000
|Cooking & Oils|Turmeric 50g|pack|1000
|Cooking & Oils|Mixed spice 50g|pack|1500
|Cooking & Oils|Royco mchuzi mix 100g|pkt|1500
|Cooking & Oils|Tomato sauce 300g|btl|2500
|Cooking & Oils|Chili sauce 300g|btl|2500
|Cooking & Oils|Vinegar 250ml|btl|2000
|Snacks & Sweets|Sweets assorted|pkt|500
|Snacks & Sweets|Toffees|pkt|500
|Snacks & Sweets|Chewing gum|pkt|500
|Snacks & Sweets|Lollipops|pc|200
|Snacks & Sweets|Biscuits glucose|pkt|1000
|Snacks & Sweets|Biscuits assorted|pkt|1500
|Bread & Bakery|Cakes snack|pc|1000
|Bread & Bakery|Doughnuts|pc|500
|Dairy & Eggs|Eggs tray of 30|tray|15000
|Dairy & Eggs|Eggs each|pc|500
|Cooking & Oils|Margarine 250g|tin|4000
|Beans & Nuts|Peanut butter 250g|jar|6000
|Beans & Nuts|Raisins 100g|pkt|2500
|Grains & Breakfast|Corn flakes 250g|box|6500
|Grains & Breakfast|Porridge flour 1kg|pack|5000
|Grains & Breakfast|Soybean flour 1kg|pack|5500
""")
        "Supermarket" -> parse("""
|Soft Drinks|Coca-Cola 300ml|btl|1500
|Soft Drinks|Coca-Cola 500ml|btl|2000
|Soft Drinks|Coca-Cola 1.5L|btl|4500
|Soft Drinks|Coca-Cola 2L|btl|6000
|Soft Drinks|Pepsi 300ml|btl|1500
|Soft Drinks|Pepsi 500ml|btl|2000
|Soft Drinks|Sprite 300ml|btl|1500
|Soft Drinks|Fanta 300ml|btl|1500
|Soft Drinks|Fanta Orange 500ml|btl|2000
|Soft Drinks|Novida 300ml|btl|1500
|Soft Drinks|Mountain Dew 500ml|btl|2000
|Soft Drinks|Krest 300ml|btl|1500
|Soft Drinks|Stoney 300ml|btl|1500
|Soft Drinks|Mirinda Fruity 300ml|btl|1500
|Water & Juices|Rwenzori Water 330ml|btl|800
|Water & Juices|Rwenzori Water 500ml|btl|1000
|Water & Juices|Rwenzori Water 1.5L|btl|2500
|Water & Juices|Azure Water 500ml|btl|1000
|Water & Juices|Alive Water 500ml|btl|1000
|Groceries|Minute Maid 500ml|pkt|2000
|Water & Juices|Nnunu Juice 500ml|pkt|2000
|Water & Juices|Rocky's Juice 250ml|pkt|1000
|Water & Juices|Bushera 500ml|bottle|2500
|Water & Juices|Milk shake 250ml|pkt|2000
|Water & Juices|Yoghurt 500ml|cup|3500
|Water & Juices|Yoghurt 1L|cup|6500
|Water & Juices|Mala 500ml|cup|3000
|Soft Drinks|Red Bull 250ml|can|5000
|Soft Drinks|Monster 500ml|can|8000
|Soft Drinks|Energy drink assorted|can|3500
|Snacks & Candy|Chips potato crisps|pkt|1000
|Snacks & Candy|Crisps assorted|pkt|1500
|Snacks & Candy|Popcorn 80g|pkt|2000
|Snacks & Candy|Niknaks|pkt|1000
|Snacks & Candy|Biscuits assorted|pkt|1500
|Snacks & Candy|Cookies 200g|pkt|3000
|Snacks & Candy|Chocolates|pc|2000
|Snacks & Candy|Bubblegum|pkt|500
|Water & Juices|Juice concentrate 1L|btl|8000
|Water & Juices|Juice concentrate 500ml|btl|4500
|Wines|Wine 750ml|btl|20000
|Tissue & Paper|Toilet paper 2-ply|roll|1500
|Tissue & Paper|Toilet paper pack of 10|pkt|14000
|Tissue & Paper|Facial tissues|pkt|2000
|Tissue & Paper|Serviettes 100s|pkt|2000
|Cleaning & Detergents|Omo 500g|pkt|4500
|Cleaning & Detergents|Omo 1kg|pkt|8500
|Cleaning & Detergents|Ariel 500g|pkt|5000
|Cleaning & Detergents|Bar soap Geisha 800g|bar|5500
|Cleaning & Detergents|Bar soap 300g|bar|2500
|Cleaning & Detergents|Petro bar soap|bar|3000
|Cleaning & Detergents|JIK bleach 500ml|btl|3500
|Cleaning & Detergents|Toilet cleaner 500ml|btl|4000
|Cleaning & Detergents|Dish soap 500ml|btl|3000
|Cleaning & Detergents|Dettol antiseptic 250ml|btl|6000
|Cleaning & Detergents|Handwash 250ml|btl|3500
|Cleaning & Detergents|Air freshener 300ml|spray|5000
|Personal Care|Toothpaste 100ml|tube|3000
|Personal Care|Toothpaste 50ml|tube|2000
|Personal Care|Toothbrush|pc|1500
|Personal Care|Mouthwash 250ml|btl|8000
|Household Items|Matchbox 10-pack|pkt|1000
|Household Items|Candles packet|pkt|2500
|Household Items|Batteries AA 2-pack|pkt|1000
|Household Items|Batteries D 2-pack|pkt|2000
|Personal Care|Razor blades packet|pkt|1500
|Personal Care|Shaving gel 150ml|btl|5000
|Baby & Feminine Care|Sanitary pads|pkt|3000
|Baby & Feminine Care|Sanitary pads 8s|pkt|4500
|Baby & Feminine Care|Baby diapers small pack|pkt|12000
|Baby & Feminine Care|Baby diapers jumbo|pkt|32000
|Baby & Feminine Care|Baby wipes|pkt|3000
|Baby & Feminine Care|Baby cereal 400g|tin|12000
|Personal Care|Cotton buds 100s|pkt|1500
|Household Items|Slippers|pair|5000
|Household Items|Socks pair|pair|2000
|Household Items|Handkerchief|pc|1000
|Household Items|Umbrella|pc|15000
|Water & Juices|Water bottle 1L|pc|5000
|Household Items|Flask 1L|pc|25000
""")
        "Hardware" -> parse("""
|Building & Construction|Cement Tororo 50kg|bag|34000
|Building & Construction|Cement Hima 50kg|bag|35000
|Building & Construction|Iron sheets 28G|pc|52000
|Building & Construction|Iron sheets 30G|pc|45000
|Building & Construction|Iron sheets 32G|pc|38000
|Building & Construction|Iron sheets 24G|pc|65000
|Building & Construction|Ridge caps|pc|15000
|Building & Construction|Roofing nails 3-inch kg|kg|10000
|Building & Construction|Round bars Y12 per length|pc|16000
|Building & Construction|Round bars Y10 per length|pc|12000
|Building & Construction|Round bars Y8 per length|pc|8000
|Building & Construction|Deformed bars 16mm length|pc|24000
|Building & Construction|Square tubes 2-inch|pc|18000
|Building & Construction|Angle bars 2-inch|pc|12000
|Building & Construction|BRC wire mesh A142|roll|45000
|Building & Construction|BRC wire mesh A193|roll|55000
|Building & Construction|Chicken wire mesh|roll|35000
|Building & Construction|Nails 2-inch kg|kg|8000
|Building & Construction|Nails 3-inch kg|kg|8000
|Building & Construction|Nails 4-inch kg|kg|8000
|Building & Construction|Wire nails 1-inch kg|kg|7500
|Building & Construction|Fired bricks|pc|500
|Building & Construction|Cement bricks|pc|700
|Building & Construction|Culvert pipes 2ft|pc|35000
|Building & Construction|Sand trip|trip|250000
|Building & Construction|Murrum trip|trip|150000
|Building & Construction|Hardcore trip|trip|180000
|Building & Construction|Aggregate 3/4 trip|trip|300000
|Building & Construction|Door ordinary|pc|120000
|Building & Construction|Door steel|pc|250000
|Building & Construction|Door frame|pc|60000
|Building & Construction|Window frame steel|pc|80000
|Building & Construction|Door lock mortice|pc|15000
|Building & Construction|Padlock large|pc|10000
|Building & Construction|Padlock small|pc|5000
|Building & Construction|Hinges pair|pair|3000
|Building & Construction|Handles|pc|2000
|Paint & Finishing|Paint Crown 20L|tin|90000
|Paint & Finishing|Paint Crown 4L|tin|22000
|Paint & Finishing|Paint Royale 20L|tin|110000
|Paint & Finishing|Undercoat 20L|tin|60000
|Paint & Finishing|Gloss enamel 4L|tin|25000
|Paint & Finishing|Lacquer 4L|tin|25000
|Paint & Finishing|Primer 20L|tin|60000
|Paint & Finishing|Paint brush 3-inch|pc|8000
|Paint & Finishing|Paint brush 2-inch|pc|5000
|Paint & Finishing|Roller + tray set|set|15000
|Paint & Finishing|Putty 1kg|pkt|3000
|Plumbing & Water|PVC pipe 4-inch 3m|pc|15000
|Plumbing & Water|PVC pipe 2-inch 3m|pc|8000
|Plumbing & Water|PVC pipe 1-inch 3m|pc|5000
|Plumbing & Water|PVC elbows 4-inch|pc|3000
|Plumbing & Water|PVC elbows 2-inch|pc|1500
|Plumbing & Water|PVC tees 4-inch|pc|4000
|Plumbing & Water|GI pipe 1-inch 6m|pc|35000
|Plumbing & Water|Basin tap|pc|12000
|Plumbing & Water|Kitchen sink tap|pc|25000
|Plumbing & Water|Shower head|pc|20000
|Plumbing & Water|Water tank 1000L|pc|350000
|Plumbing & Water|Water tank 5000L|pc|1200000
|Electricals|Cable 1.5mm roll|roll|95000
|Electricals|Cable 2.5mm roll|roll|145000
|Electricals|Cable 4mm roll|roll|240000
|Electricals|Sockets 13A|pc|5000
|Electricals|Switches 5A|pc|4000
|Electricals|Switch socket combo|pc|7000
|Electricals|Distribution board 8-way|pc|45000
|Electricals|MCB 32A|pc|8000
|Electricals|Bulb LED 9W|pc|8000
|Electricals|Bulb LED 18W|pc|12000
|Electricals|Fluorescent tube 4ft|pc|15000
|Electricals|Bulb holder|pc|1500
|Electricals|Extension cable 4-way|pc|15000
|Solar & Power|Solar panel 100W|pc|250000
|Solar & Power|Solar panel 200W|pc|450000
|Solar & Power|Solar battery 100Ah|pc|450000
|Solar & Power|Inverter 1000W|pc|450000
|Tools|Hoe flat|pc|15000
|Tools|Hoe forked|pc|16000
|Tools|Panga|pc|12000
|Tools|Slashers|pc|15000
|Tools|Crowbar|pc|20000
|Tools|Pick axe|pc|22000
|Tools|Spade|pc|25000
|Tools|Shovel|pc|25000
|Tools|Wheelbarrow|pc|120000
|Tools|Hammer claw|pc|15000
|Tools|Hammer sledge|pc|25000
|Tools|Spirit level 2ft|pc|15000
|Plumbing & Water|Measuring tape 5m|pc|8000
|Tools|Trowel|pc|10000
|Tools|Trowel pointing|pc|8000
|Tools|Chisel 1-inch|pc|5000
|Tools|File flat 10-inch|pc|6000
|Tools|Pliers combination|pc|10000
|Tools|Pliers nose|pc|8000
|Tools|Adjustable wrench 12-inch|pc|15000
|Tools|Hacksaw + blades|set|20000
|Tools|Hand saw|pc|20000
|Tools|Try square|pc|5000
|Tools|Welding rods 2.5mm kg|kg|12000
|Tools|Welding rods 3.15mm kg|kg|12000
|Safety & Workwear|Gloves leather pair|pair|8000
|Safety & Workwear|Gum boots|pair|25000
|Safety & Workwear|Overall|pc|25000
|Safety & Workwear|Dust coat|pc|20000
|Safety & Workwear|Safety helmet|pc|15000
|Safety & Workwear|Mask N95|pc|2000
|Safety & Workwear|Ropes sisal 20m|roll|8000
|Safety & Workwear|Tarpaulin 5x6m|pc|40000
""")
        "Pharmacy / Drug Shop" -> parse("""
|Pain & Fever|Panadol strip|strip|2000
|Pain & Fever|Panadol Extra strip|strip|2500
|Pain & Fever|Panadol syrup 60ml|btl|4000
|Pain & Fever|Panadol infant syrup|btl|4500
|Malaria Treatment|Coartem pack|pack|5000
|Malaria Treatment|Coartem 20/120|pack|5500
|Malaria Treatment|Artemether injection|vial|5000
|Malaria Treatment|Quinine injection|vial|4000
|Malaria Treatment|Quinine tablets strip|strip|2000
|Antibiotics|Amoxicillin 250mg pack|pack|3000
|Antibiotics|Amoxicillin 500mg pack|pack|4000
|Antibiotics|Ampiclox capsules|pack|5000
|Antibiotics|Ciprofloxacin 500 strip|strip|2500
|Antibiotics|Metronidazole strip|strip|1500
|Antibiotics|Metronidazole syrup|btl|4000
|Antibiotics|Azithromycin pack|pack|6000
|Antibiotics|Cephalexin capsules|pack|4500
|Antibiotics|Gentamicin injection|vial|3000
|Antibiotics|Penicillin injection|vial|3500
|Stomach & Digestive|ORS sachets pack|pack|2000
|Stomach & Digestive|Zinc tablets pack|pack|2500
|Pain & Fever|Ibuprofen strip|strip|1500
|Pain & Fever|Ibuprofen syrup 100ml|btl|5000
|Pain & Fever|Diclofenac strip|strip|1500
|Pain & Fever|Diclofenac injection|vial|2500
|Pain & Fever|Aspirin strip|strip|1000
|Pain & Fever|Indocid strip|strip|2000
|Stomach & Digestive|Antacid bottle|btl|5000
|Stomach & Digestive|Antacid tablets|pkt|3000
|Stomach & Digestive|Omeprazole pack|pack|4000
|Stomach & Digestive|Buscopan strip|strip|3000
|Allergy & Cough|Chlorpheniramine strip|strip|1500
|Allergy & Cough|Piriton strip|strip|1500
|Allergy & Cough|Cetirizine strip|strip|2000
|Allergy & Cough|Loratadine strip|strip|2000
|Allergy & Cough|Prednisolone strip|strip|2000
|Allergy & Cough|Dexamethasone strip|strip|2500
|Chronic & Prescription|Diazepam strip|strip|2000
|Chronic & Prescription|Carbamazepine pack|pack|5000
|Chronic & Prescription|Phenobarbitone strip|strip|2000
|Chronic & Prescription|Amitriptyline strip|strip|2500
|Antifungal & Skin|Fluconazole pack|pack|5000
|Antifungal & Skin|Clotrimazole cream|tube|5000
|Antifungal & Skin|Miconazole cream|tube|5000
|Antifungal & Skin|Hydrocortisone cream|tube|6000
|Antifungal & Skin|Betamethasone cream|tube|6000
|Antifungal & Skin|Calamine lotion|btl|4000
|Allergy & Cough|Cough syrup 100ml|btl|6000
|Allergy & Cough|Cough lozenges|pkt|2000
|Allergy & Cough|Salbutamol syrup|btl|5000
|Allergy & Cough|Ventolin inhaler|pc|25000
|Allergy & Cough|Cough linctus|btl|5000
|Vitamins & Supplements|Multivitamin tablets|pack|5000
|Vitamins & Supplements|Vitamin C pack|pack|3000
|Vitamins & Supplements|Vitamin B-complex|pack|3500
|Vitamins & Supplements|Iron supplements pack|pack|4000
|Vitamins & Supplements|Folic acid strip|strip|1500
|Vitamins & Supplements|Ferrous sulphate pack|pack|3000
|Vitamins & Supplements|Calcium tablets|pkt|4000
|Stomach & Digestive|Zincovit syrup|btl|6000
|Vitamins & Supplements|Omega 3 capsules|pkt|8000
|Chronic & Prescription|Insulin vial|vial|45000
|Chronic & Prescription|Metformin pack|pack|4000
|Chronic & Prescription|Glibenclamide strip|strip|2000
|Chronic & Prescription|Amlodipine strip|strip|2500
|Chronic & Prescription|Nifedipine strip|strip|2500
|Chronic & Prescription|Atenolol strip|strip|2500
|Chronic & Prescription|Losartan pack|pack|8000
|Chronic & Prescription|Enalapril strip|strip|3000
|Chronic & Prescription|Furosemide strip|strip|2000
|Chronic & Prescription|HCTZ strip|strip|1500
|Wound Care & Antiseptics|Cotton wool 100g|roll|2000
|Wound Care & Antiseptics|Cotton wool 500g|roll|7000
|Wound Care & Antiseptics|Bandage roll|roll|2000
|Wound Care & Antiseptics|Bandage crepe|roll|4000
|Wound Care & Antiseptics|Plaster roll|roll|1500
|Wound Care & Antiseptics|Plaster strips|pkt|2000
|Wound Care & Antiseptics|Gauze swabs|pkt|3000
|Wound Care & Antiseptics|Surgical spirit 250ml|btl|3000
|Wound Care & Antiseptics|Surgical spirit 500ml|btl|5500
|Medical Supplies|Surgical gloves pair|pair|1000
|Medical Supplies|Examination gloves box|box|15000
|Medical Supplies|Syringes 5ml|pc|500
|Medical Supplies|Syringes 2ml|pc|400
|Medical Supplies|Syringes 10ml|pc|700
|Medical Supplies|Needles 21G packet|pkt|2000
|Medical Supplies|Needles 23G packet|pkt|2000
|Medical Supplies|IV giving set|pc|2500
|Medical Supplies|IV cannula 18G|pc|2000
|Malaria Treatment|Malaria test kit RDT|pc|2000
|Medical Supplies|Typhoid test kit|pc|3000
|Medical Supplies|Pregnancy test kit|pc|3000
|Medical Supplies|Urine test strips|pkt|5000
|Over-the-Counter|Blood glucose strips|pkt|15000
|Medical Supplies|Digital thermometer|pc|10000
|Medical Supplies|BP machine manual|pc|120000
|Medical Supplies|Stethoscope|pc|50000
|Medical Supplies|Weighing scale|pc|60000
|Medical Supplies|Condoms packet|pkt|2000
|Wound Care & Antiseptics|Betadine 100ml|btl|5000
|Wound Care & Antiseptics|Hydrogen peroxide 100ml|btl|3000
|Eye, Ear & Nose|Eye drops chloramphenicol|btl|5000
|Allergy & Cough|Eye drops dexamethasone|btl|8000
|Eye, Ear & Nose|Ear drops|btl|6000
|Eye, Ear & Nose|Nasal spray|btl|10000
|Wound Care & Antiseptics|Antiseptic soap|bar|3000
|Wound Care & Antiseptics|Hand sanitizer 250ml|btl|4000
|Medical Supplies|Face masks box of 50|box|10000
|Medical Supplies|First aid kit|set|40000
""")
        "Agro-Vet & Farm" -> parse("""
|Seeds|Maize seed Longe 5 1kg|kg|6000
|Seeds|Maize seed Longe 10 1kg|kg|8000
|Seeds|Bean seed 1kg|kg|8000
|Seeds|Bean seed K132 1kg|kg|9000
|Seeds|Sorghum seed 1kg|kg|5000
|Seeds|Millet seed 1kg|kg|6000
|Seeds|Rice seed 1kg|kg|6000
|Seeds|Sunflower seed 1kg|kg|7000
|Seeds|Soybean seed 1kg|kg|7500
|Seeds|Groundnut seed 1kg|kg|9000
|Fertilizers & Manure|DAP 50kg|bag|180000
|Fertilizers & Manure|NPK 17-17-17 50kg|bag|190000
|Fertilizers & Manure|NPK 25-5-5 50kg|bag|185000
|Fertilizers & Manure|Urea 50kg|bag|170000
|Fertilizers & Manure|CAN 50kg|bag|160000
|Fertilizers & Manure|TSP 50kg|bag|175000
|Fertilizers & Manure|MOP 50kg|bag|200000
|Fertilizers & Manure|Manure bag|bag|10000
|Fertilizers & Manure|Lime agricultural 50kg|bag|45000
|Fertilizers & Manure|Foliar feed 1L|btl|15000
|Fertilizers & Manure|Foliar feed 250ml|btl|6000
|Tools & Irrigation|Sprayer 16L knapsack|pc|120000
|Tools & Irrigation|Sprayer 8L manual|pc|60000
|Tools & Irrigation|Sprayer parts lance|pc|8000
|Tools & Irrigation|Sprayer nozzles|pkt|3000
|Tools & Irrigation|Spray boots|pair|25000
|Tools & Irrigation|Overalls agro|pc|25000
|Crop Chemicals|Dudu Acelle 100ml|btl|15000
|Crop Chemicals|Rocket 100ml|btl|12000
|Crop Chemicals|Weed Master 100ml|btl|10000
|Crop Chemicals|Roundup 1L|btl|30000
|Crop Chemicals|Gramoxone 1L|btl|28000
|Crop Chemicals|Lazer 100ml|btl|12000
|Crop Chemicals|Dudufen 100g|pkt|10000
|Crop Chemicals|Mancozeb 1kg|pkt|18000
|Crop Chemicals|Copper oxychloride 1kg|pkt|15000
|Crop Chemicals|Ridomil 500g|pkt|45000
|Crop Chemicals|Actara 50g|pkt|20000
|Crop Chemicals|Virtako 40g|pkt|15000
|Crop Chemicals|Ambush 100ml|btl|12000
|Crop Chemicals|Dipterex 500g|pkt|10000
|Crop Chemicals|Acaramoth 100ml|btl|15000
|Animal Health|Cattle dip 1L|btl|25000
|Animal Health|Albendazole 500ml|btl|25000
|Animal Health|Ivermectin injection 50ml|btl|20000
|Animal Health|Oxytetracycline 500ml|btl|30000
|Animal Health|Penstripp 500ml|btl|35000
|Feeds|Liver block 50kg|bag|130000
|Feeds|Growers mash 50kg|bag|125000
|Feeds|Layers mash 50kg|bag|130000
|Poultry & Equipment|Chick starter 50kg|bag|140000
|Feeds|Broiler starter 50kg|bag|145000
|Feeds|Broiler finisher 50kg|bag|140000
|Poultry & Equipment|Poultry vitamins 100g|pkt|8000
|Poultry & Equipment|Poultry vaccination ND vial|vial|10000
|Poultry & Equipment|Gumboro vaccine vial|vial|12000
|Poultry & Equipment|Fowl typhoid vaccine|vial|10000
|Poultry & Equipment|Day-old chicks|pc|3500
|Poultry & Equipment|Point of lay pullets|pc|25000
|Poultry & Equipment|Egg trays|tray|500
|Poultry & Equipment|Feeders poultry|pc|12000
|Poultry & Equipment|Drinkers poultry|pc|10000
|Poultry & Equipment|Poultry cages 120-bird|pc|900000
|Poultry & Equipment|Brooder lamps|pc|10000
|Feeds|Pig feed 50kg|bag|135000
|Feeds|Pig creep feed 50kg|bag|150000
|Feeds|Rabbit pellets 50kg|bag|140000
|Poultry & Equipment|Rabbit cages|pc|50000
|Feeds|Dog food 20kg|bag|130000
|Feeds|Cat food 10kg|bag|100000
|Animal Health|Pet vaccines|vial|15000
|Animal Health|Dewormers dogs|pkt|8000
|Tools & Irrigation|Grafting tools set|set|10000
|Tools & Irrigation|Pruning shears|pc|12000
|Tools & Irrigation|Secateurs|pc|10000
|Tools & Irrigation|Budding tape|roll|2000
|Tools & Irrigation|Shade net 3x5m|pc|25000
|Tools & Irrigation|Mulch paper 50m|roll|45000
|Tools & Irrigation|Drip lines 100m|roll|90000
|Tools & Irrigation|Irrigation pipes 2-inch 30m|roll|120000
|Tools & Irrigation|Water pumps 5.5HP|pc|1200000
|Feeds|Fish feed 50kg|bag|180000
|Tools & Irrigation|Fish pond liners|pc|250000
""")
        "Butchery" -> parse("""
|Beef|Beef boneless|kg|16000
|Beef|Beef with bones|kg|14000
|Beef|Beef mince|kg|17000
|Goat & Mutton|Goat meat|kg|18000
|Goat & Mutton|Mutton|kg|18000
|Pork|Pork|kg|15000
|Pork|Pork ribs|kg|16000
|Poultry & Birds|Chicken whole fresh|kg|14000
|Poultry & Birds|Chicken whole frozen|kg|12000
|Poultry & Birds|Chicken parts|kg|15000
|Poultry & Birds|Chicken breasts|kg|18000
|Poultry & Birds|Turkey|kg|25000
|Poultry & Birds|Duck|kg|20000
|Beef|Liver beef|kg|10000
|Poultry & Birds|Liver chicken|kg|12000
|Offal & Bones|Kidney|kg|8000
|Offal & Bones|Tripe|kg|8000
|Offal & Bones|Ox tail|kg|12000
|Offal & Bones|Ox tongue|kg|12000
|Offal & Bones|Tendon|kg|7000
|Sausages & Processed|Sausages raw|kg|18000
|Sausages & Processed|Sausages smoked|kg|20000
|Sausages & Processed|Smokies pack of 10|pkt|8000
|Sausages & Processed|Bacon 500g|pkt|15000
|Sausages & Processed|Ham 500g|pkt|15000
|Sausages & Processed|Salami 500g|pkt|18000
|Sausages & Processed|Smoked meat|kg|20000
|Sausages & Processed|Dried meat strips|kg|25000
|Sausages & Processed|Suet|kg|5000
|Offal & Bones|Bones for soup|kg|3000
|Offal & Bones|Offal mixed|kg|6000
|Poultry & Birds|Chicken gizzards|kg|9000
|Poultry & Birds|Chicken feet|kg|5000
|Poultry & Birds|Chicken heads|kg|4000
|Eggs|Eggs tray 30|tray|15000
|Supplies & Equipment|Charcoal bag|bag|40000
|Supplies & Equipment|Butcher knives|pc|15000
|Supplies & Equipment|Cutting boards|pc|20000
|Supplies & Equipment|Meat hooks|pc|5000
|Supplies & Equipment|Cling film 300m|roll|15000
|Offal & Bones|Cooler box large|pc|150000
""")
        "Salon & Cosmetics" -> parse("""
|Hair Care|Hair relaxer 500ml|tin|15000
|Hair Care|Hair relaxer 1L|tin|25000
|Hair Care|Dark & Lovely kit|kit|20000
|Hair Care|Motions relaxer kit|kit|25000
|Hair Care|Shampoo 500ml|btl|12000
|Hair Care|Shampoo 1L|btl|20000
|Hair Care|Conditioner 500ml|btl|12000
|Hair Care|Conditioner 1L|btl|20000
|Hair Care|Hair food 250g|tin|8000
|Hair Care|Hair food 500g|tin|14000
|Skin & Body Care|Petroleum jelly 250g|tin|4000
|Skin & Body Care|Petroleum jelly 100g|tin|2000
|Skin & Body Care|Coconut oil 250ml|btl|10000
|Skin & Body Care|Castor oil 250ml|btl|8000
|Skin & Body Care|Olive oil 250ml|btl|10000
|Skin & Body Care|Shea butter 250g|jar|10000
|Skin & Body Care|Cocoa butter 250g|jar|10000
|Skin & Body Care|Body lotion 500ml|btl|10000
|Skin & Body Care|Body lotion 250ml|btl|6000
|Skin & Body Care|Body butter 250g|jar|12000
|Skin & Body Care|Body wash 500ml|btl|8000
|Skin & Body Care|Body soap bar|bar|3000
|Skin & Body Care|Herbal soap|bar|3500
|Skin & Body Care|Face wash 150ml|tube|8000
|Skin & Body Care|Face cream 100ml|jar|10000
|Skin & Body Care|Face mask 100g|pkt|8000
|Skin & Body Care|Scrubs 250g|pkt|10000
|Hair Color & Styling|Toners 200ml|btl|10000
|Makeup|Foundation 30ml|btl|12000
|Makeup|Concealer|pc|8000
|Makeup|Powder compact|pc|8000
|Makeup|Face powder jar|jar|6000
|Makeup|Blush|pc|7000
|Makeup|Bronzer|pc|8000
|Makeup|Highlighter|pc|10000
|Makeup|Contour kit|kit|20000
|Makeup|Eyebrow pencil|pc|3000
|Makeup|Eyeliner|pc|5000
|Makeup|Mascara|pc|8000
|Makeup|Eyeshadow palette|pc|15000
|Makeup|Lipstick|pc|5000
|Makeup|Lip gloss|pc|4000
|Makeup|Lip liner|pc|3000
|Makeup|Lip balm|pc|2000
|Makeup|Makeup brushes set|set|25000
|Makeup|Makeup sponges|pkt|3000
|Makeup|Makeup remover 200ml|btl|8000
|Makeup|Makeup mirror|pc|8000
|Beauty Supplies|False eyelashes|pair|5000
|Nails|Nail polish|pc|3000
|Nails|Nail polish remover 250ml|btl|4000
|Nails|Nail files|pc|1000
|Nails|Nail clippers|pc|3000
|Nails|Nail extensions kit|kit|25000
|Nails|Acrylic nail kit|kit|50000
|Skin & Body Care|Cuticle oil|btl|5000
|Skin & Body Care|Hand cream 100ml|tube|7000
|Nails|Cuticle pusher|pc|2000
|Hair Extensions|Weave human hair|pc|50000
|Hair Extensions|Weave synthetic|pc|25000
|Hair Extensions|Braids packet|pkt|15000
|Hair Extensions|Braids jumbo|pkt|20000
|Hair Extensions|Crochet hair|pkt|25000
|Hair Extensions|Wig lace front|pc|120000
|Hair Extensions|Wig ordinary|pc|50000
|Hair Extensions|Hair closure|pc|30000
|Hair Extensions|Hair bundles|pkt|70000
|Hair Color & Styling|Hair coloring kit|kit|25000
|Hair Color & Styling|Hair bleach kit|kit|30000
|Hair Color & Styling|Hair toner|btl|15000
|Hair Color & Styling|Hair spray 250ml|btl|10000
|Hair Color & Styling|Hair mousse 250ml|btl|10000
|Hair Color & Styling|Hair gel 250g|jar|5000
|Hair Color & Styling|Hair gel 500g|jar|8000
|Hair Color & Styling|Hair oil 200ml|btl|8000
|Hair Color & Styling|Heat protectant|btl|12000
|Hair Color & Styling|Hair masks 500ml|btl|15000
|Combs & Tools|Combs assorted|pc|2000
|Combs & Tools|Combs afro|pc|3000
|Combs & Tools|Hair brushes|pc|5000
|Combs & Tools|Rat tail comb|pc|2000
|Combs & Tools|Hair pins packet|pkt|1000
|Combs & Tools|Bobby pins packet|pkt|1000
|Combs & Tools|Hair clips|pkt|2000
|Combs & Tools|Headbands|pc|3000
|Beauty Supplies|Scarves|pc|5000
|Combs & Tools|Durags|pc|5000
|Combs & Tools|Bonnets satin|pc|8000
|Combs & Tools|Hair dryers|pc|80000
|Combs & Tools|Curling irons|pc|60000
|Combs & Tools|Straighteners flat|pc|70000
|Combs & Tools|Hair clippers|pc|80000
|Combs & Tools|Trimmers|pc|60000
|Combs & Tools|Clipper blades|pc|15000
|Combs & Tools|Razor blades packet|pkt|1500
|Shaving & Grooming|Shaving cream|btl|5000
|Shaving & Grooming|Aftershave 250ml|btl|10000
|Skin & Body Care|Beard oil 50ml|btl|12000
|Shaving & Grooming|Beard kit|kit|30000
|Salon Equipment|Barber capes|pc|15000
|Salon Equipment|Neck strips|roll|3000
|Salon Equipment|Towels salon|pc|8000
|Salon Equipment|Mirrors wall|pc|25000
|Salon Equipment|Salon chairs|pc|300000
|Salon Equipment|Hair washing basin|pc|150000
""")
        "Stationery / Bookshop" -> parse("""
|Exercise Books & Paper|Exercise book 32pg sq|pc|1000
|Exercise Books & Paper|Exercise book 48pg sq|pc|1500
|Exercise Books & Paper|Exercise book 64pg sq|pc|2000
|Exercise Books & Paper|Exercise book 96pg sq|pc|2500
|Exercise Books & Paper|Counter book 2-quire|pc|7000
|Exercise Books & Paper|Counter book 4-quire|pc|12000
|Exercise Books & Paper|Hardcover book 2-quire|pc|15000
|Exercise Books & Paper|Hardcover book 4-quire|pc|25000
|Exercise Books & Paper|Ruled refills packet|pkt|3000
|Exercise Books & Paper|Plain refills packet|pkt|3000
|Exercise Books & Paper|Squared paper packet|pkt|3000
|Exercise Books & Paper|Graph paper 5mm|pkt|3000
|Writing Instruments|Pens blue Bic|pc|500
|Writing Instruments|Pens black Bic|pc|500
|Writing Instruments|Pens red Bic|pc|500
|Writing Instruments|Pens green Bic|pc|500
|Writing Instruments|Gel pens|pc|1000
|General Stationery|Ballpoint assorted box|box|12000
|Writing Instruments|Pencils HB|pc|500
|Writing Instruments|Pencils 2B|pc|500
|Writing Instruments|Pencils colored box of 12|box|8000
|Writing Instruments|Pencil sharpeners|pc|500
|Writing Instruments|Erasers|pc|500
|General Stationery|Rulers 30cm|pc|1000
|General Stationery|Rulers 15cm|pc|500
|Art & Craft|Set squares|pc|1500
|Art & Craft|Protractors|pc|1000
|Writing Instruments|Compass pencil|pc|1500
|Writing Instruments|Geometry box set|set|3000
|General Stationery|Calculators basic|pc|15000
|General Stationery|Calculators scientific|pc|45000
|Writing Instruments|Crayons 12-pack|pkt|3000
|Writing Instruments|Crayons 24-pack|pkt|5000
|Writing Instruments|Colored pencils 12|pkt|4000
|Writing Instruments|Felt pens 12|pkt|5000
|Writing Instruments|Markers whiteboard|pc|2000
|Writing Instruments|Markers permanent|pc|2000
|Writing Instruments|Highlighters|pc|2000
|Writing Instruments|Highlighters set of 4|set|6000
|Office & Filing|Glue sticks 20g|pc|2000
|Office & Filing|Glue bottle 100ml|btl|3000
|Office & Filing|Paper glue 250ml|btl|4000
|Office & Filing|Staplers|pc|8000
|Office & Filing|Staples pins|pkt|1500
|Office & Filing|Staple remover|pc|3000
|Office & Filing|Paper clips 100s|box|2000
|General Stationery|Push pins 100s|box|2000
|Office & Filing|Drawing pins|box|2000
|Office & Filing|Rubber bands 100g|pkt|2000
|Exercise Books & Paper|A4 paper ream 70gsm|ream|25000
|Exercise Books & Paper|A4 paper ream 80gsm|ream|28000
|Exercise Books & Paper|A3 paper ream|ream|55000
|Exercise Books & Paper|Photocopy paper rim|ream|25000
|Office & Filing|Files folders A4|pc|2000
|Office & Filing|Box files|pc|12000
|Office & Filing|Lever arch files|pc|18000
|Office & Filing|Expanding files|pc|15000
|Office & Filing|Display books A4|pc|8000
|Office & Filing|Ring binders 2-ring|pc|6000
|Office & Filing|Plastic sleeves A4|pkt|3000
|Office & Filing|Punch 2-hole|pc|8000
|Office & Filing|Scissors small|pc|2000
|Office & Filing|Scissors large|pc|4000
|Exercise Books & Paper|Cutting mats A4|pc|10000
|Art & Craft|Craft knives|pc|3000
|Office & Filing|Tape rolls clear|roll|1500
|Office & Filing|Tape rolls double-sided|roll|3000
|Office & Filing|Masking tape|roll|2500
|Office & Filing|Duct tape|roll|5000
|Office & Filing|Envelopes A4 white|pkt|3000
|Office & Filing|Envelopes A5 brown|pkt|2500
|Office & Filing|Envelopes padded|pc|1000
|Office & Filing|Bubble wrap 1m|roll|5000
|School Items|School bags|pc|30000
|School Items|Lunch boxes|pc|10000
|School Items|Water bottles school|pc|8000
|School Items|Maths sets primary|set|5000
|Exercise Books & Paper|Charts educational|pc|5000
|Exercise Books & Paper|Globe|pc|25000
|Writing Instruments|Chalk box of 100|box|5000
|Writing Instruments|Whiteboard markers pack|pkt|6000
|School Items|Whiteboard dusters|pc|2000
|School Items|Blackboard paint|tin|25000
|Exercise Books & Paper|Index cards 100s|pkt|3000
|Exercise Books & Paper|Sticky notes 100s|pkt|2500
|Exercise Books & Paper|Notebooks spiral A5|pc|5000
|Exercise Books & Paper|Notebooks hard A5|pc|6000
|Exercise Books & Paper|Diaries 2026|pc|15000
|Exercise Books & Paper|Planners 2026|pc|18000
|Office & Filing|Certificates A4 50s|pkt|15000
|Office & Filing|Lamination pouches A4 50s|pkt|12000
|Office & Filing|Ink pads|pc|3000
|Office & Filing|Rubber stamps custom|pc|25000
|Office & Filing|Date stamps|pc|20000
""")
        "Bar & Restaurant" -> parse("""
|Beers|Beer Nile Special|btl|6000
|Beers|Beer Nile Gold|btl|5000
|Beers|Beer Club|btl|5000
|Beers|Beer Club Pilsner|btl|5000
|Beers|Beer Bell Lager|btl|5000
|Beers|Beer Bell Ale|btl|5500
|Beers|Beer Guinness|btl|5500
|Beers|Beer Tusker Lite|btl|5500
|Beers|Beer Tusker Malt|btl|5500
|Beers|Beer Senator|btl|4000
|Beers|Beer Ngoma|btl|4000
|Beers|Beer Eagle Lager|btl|4500
|Beers|Beer canned assorted|can|5500
|Spirits & Waragi|Waragi 350ml|btl|5000
|Spirits & Waragi|Waragi 750ml|btl|15000
|Spirits & Waragi|Konyagi 350ml|btl|5000
|Spirits & Waragi|Bond 7 750ml|btl|25000
|Spirits & Waragi|Uganda Waragi coffee 350ml|btl|5500
|Spirits & Waragi|Vodka 750ml|btl|20000
|Spirits & Waragi|Gin 750ml|btl|18000
|Spirits & Waragi|Rum 750ml|btl|22000
|Spirits & Waragi|Whisky 750ml|btl|35000
|Spirits & Waragi|Brandy 750ml|btl|25000
|Wines|Wine red 750ml|btl|20000
|Wines|Wine white 750ml|btl|20000
|Wines|Wine sweet 1L|btl|15000
|Wines|Sparkling wine|btl|45000
|Soft Drinks & Water|Soda 500ml assorted|btl|2500
|Soft Drinks & Water|Soda 300ml assorted|btl|1500
|Soft Drinks & Water|Energy drink can|can|4000
|Soft Drinks & Water|Bottled water 500ml|btl|1000
|Soft Drinks & Water|Juice fresh glass|glass|3000
|Soft Drinks & Water|Juice packed 500ml|pkt|2000
|Spirits & Waragi|Cocktail assorted|glass|10000
|Local Dishes|Rice plate dish|plate|5000
|Local Dishes|Posho & beans|plate|4000
|Local Dishes|Posho & beef|plate|7000
|Local Dishes|Matooke & beef|plate|8000
|Local Dishes|Matooke & beans|plate|5000
|Local Dishes|Matooke & groundnut|plate|6000
|Local Dishes|Chips plain|plate|5000
|Local Dishes|Chips & chicken|plate|10000
|Local Dishes|Chips & sausage|plate|7000
|Local Dishes|Chips & beef|plate|8000
|Local Dishes|Roast chicken whole|pc|20000
|Local Dishes|Roast chicken portion|plate|12000
|Local Dishes|Grilled fish tilapia|plate|15000
|Local Dishes|Fish stew|plate|12000
|Local Dishes|Beef stew|plate|8000
|Local Dishes|Goat stew|plate|10000
|Local Dishes|Pork kilo|kg|15000
|Local Dishes|Pork dish|plate|10000
|Snacks|Muchomo assorted|plate|8000
|Snacks|Rolex plain|pc|3000
|Snacks|Rolex with sausage|pc|4000
|Snacks|Chapati plain|pc|1000
|Snacks|Chapati with eggs|pc|2500
|Snacks|Samosa|pc|500
|Snacks|Mandazi|pc|500
|Snacks|Bananas fried|plate|2000
|Snacks|Kikomando|plate|3500
|Local Dishes|Salad plate|plate|3000
|Local Dishes|Soup chicken|bowl|4000
|Hot Drinks|Tea cup|cup|1000
|Hot Drinks|Coffee cup|cup|2000
|Hot Drinks|African tea|cup|1500
|Hot Drinks|Chocolate drink|cup|2500
|Hot Drinks|Milk tea|cup|1500
|Hot Drinks|Porridge cup|cup|1000
|Local Dishes|Breakfast eggs|plate|5000
|Local Dishes|Toast bread|plate|3000
|Local Dishes|Beans plain|bowl|3000
|Local Dishes|Gnuts sauce|bowl|4000
|Local Dishes|Spaghetti plate|plate|5000
|Local Dishes|Pizza slice|pc|5000
|Local Dishes|Burger|pc|10000
|Local Dishes|Shawarma|pc|8000
|Soft Drinks & Water|Milkshake|glass|6000
|Soft Drinks & Water|Smoothie assorted|glass|6000
|Soft Drinks & Water|Ice cream scoop|pc|2000
|Cigarettes & Extras|Cigarettes pack|pkt|10000
|Cigarettes & Extras|Matchbox|pc|200
|Cigarettes & Extras|Sufuria hire|pc|2000
""")
        "Mobile Money & Airtime" -> parse("""
|Airtime|MTN airtime 500|pc|500
|Airtime|MTN airtime 1000|pc|1000
|Airtime|MTN airtime 2000|pc|2000
|Airtime|MTN airtime 5000|pc|5000
|Airtime|MTN airtime 10000|pc|10000
|Airtime|MTN airtime 20000|pc|20000
|Airtime|Airtel airtime 500|pc|500
|Airtime|Airtel airtime 1000|pc|1000
|Airtime|Airtel airtime 2000|pc|2000
|Airtime|Airtel airtime 5000|pc|5000
|Airtime|Airtel airtime 10000|pc|10000
|Airtime|Airtel airtime 20000|pc|20000
|Airtime|Lycamobile airtime 1000|pc|1000
|Data & Bundles|Data bundle 1.5GB|pc|3000
|Data & Bundles|Data bundle 7GB|pc|10000
|Data & Bundles|Data bundle 15GB|pc|20000
|Data & Bundles|Data bundle 30GB|pc|40000
|Data & Bundles|Data bundle daily 1GB|pc|1000
|Data & Bundles|WhatsApp bundle daily|pc|500
|Data & Bundles|WhatsApp bundle weekly|pc|3000
|Data & Bundles|WhatsApp bundle monthly|pc|12000
|Data & Bundles|YouTube bundle weekly|pc|5000
|Data & Bundles|Social media bundle daily|pc|1000
|Data & Bundles|Minutes bundle MTN 60min|pc|3000
|Data & Bundles|Minutes bundle Airtel 60min|pc|3000
|Data & Bundles|International minutes 50min|pc|20000
|SIM & Registration|MTN SIM card|pc|2000
|SIM & Registration|Airtel SIM card|pc|2000
|SIM & Registration|SIM replacement|pc|5000
|SIM & Registration|SIM registration|pc|1000
|Agent Fees & Float|MoMo withdrawal fee|pc|500
|Agent Fees & Float|Airtel Money withdrawal fee|pc|500
|Agent Fees & Float|Send money fee|pc|500
|Agent Fees & Float|Deposit to wallet|pc|0
|Agent Fees & Float|MTN MoMo cash-out 10k|pc|400
|Agent Fees & Float|MoMo agent commission earned|pc|0
|Phone Accessories|Phone chargers|pc|8000
|Phone Accessories|Charger cables USB-C|pc|5000
|Phone Accessories|Charger cables micro-USB|pc|4000
|Phone Accessories|Charger cables iPhone|pc|8000
|Phone Accessories|Power banks 10000mAh|pc|50000
|Phone Accessories|Earphones wired|pc|5000
|Phone Accessories|Earphones wireless|pc|30000
|Phone Accessories|Bluetooth speakers|pc|45000
|Phone Accessories|Phone covers assorted|pc|3000
|Phone Accessories|Screen protectors|pc|2000
|Phone Accessories|Phone holders|pc|5000
|Phone Accessories|Memory cards 16GB|pc|15000
|Phone Accessories|Memory cards 32GB|pc|25000
|Phone Accessories|USB flash 32GB|pc|30000
|Repair Services|Phone screen repair|pc|30000
|Phone Accessories|Software flashing|pc|15000
|Repair Services|Phone unlocking|pc|10000
|Phone Accessories|Accessories assorted|pc|5000
""")
        "Produce & Grains" -> parse("""
|Grains & Cereals|Maize grain 1kg|kg|1500
|Flours & Milled|Maize bran 50kg|bag|40000
|Flours & Milled|Maize flour 1kg|kg|3500
|Flours & Milled|Maize flour 10kg|bag|30000
|Flours & Milled|Maize flour 25kg|bag|70000
|Grains & Cereals|Maize cob 100kg bag|bag|120000
|Grains & Cereals|Beans yellow 1kg|kg|5000
|Grains & Cereals|Beans red 1kg|kg|5000
|Grains & Cereals|Beans mixed 100kg|bag|480000
|Grains & Cereals|Soybeans 1kg|kg|5000
|Nuts & Seeds|Groundnuts shells 1kg|kg|5000
|Nuts & Seeds|Groundnuts clean 1kg|kg|7000
|Nuts & Seeds|Simsim 1kg|kg|9000
|Nuts & Seeds|Sunflower seeds 1kg|kg|6000
|Grains & Cereals|Rice paddy 1kg|kg|2500
|Flours & Milled|Rice milled Kaiso 25kg|bag|110000
|Flours & Milled|Rice milled Super 25kg|bag|155000
|Grains & Cereals|Millet 1kg|kg|4000
|Grains & Cereals|Sorghum 1kg|kg|3000
|Tubers & Root Crops|Cassava fresh 1kg|kg|1000
|Tubers & Root Crops|Cassava flour 1kg|kg|3000
|Tubers & Root Crops|Sweet potatoes 1kg|kg|1500
|Tubers & Root Crops|Irish potatoes 1kg|kg|2000
|Fruits|Bananas bunch medium|bunch|15000
|Fruits|Bananas bunch large|bunch|25000
|Fruits|Matooke 1kg|kg|2000
|Fruits|Bananas sweet 1kg|kg|2000
|Fresh Vegetables|Tomatoes 1kg|kg|3000
|Fresh Vegetables|Onions 1kg|kg|4000
|Fresh Vegetables|Cabbage head|pc|3000
|Fresh Vegetables|Carrots 1kg|kg|4000
|Fresh Vegetables|Green peppers 1kg|kg|5000
|Fresh Vegetables|Dodo 1kg|kg|2000
|Fresh Vegetables|Nakati 1kg|kg|2000
|Fresh Vegetables|Sukuma wiki 1kg|kg|2000
|Fresh Vegetables|Eggplants 1kg|kg|3000
|Fresh Vegetables|Cauliflower head|pc|4000
|Fresh Vegetables|Broccoli head|pc|4000
|Fresh Vegetables|Cucumber 1kg|kg|2500
|Fresh Vegetables|Garlic 100g|pkt|2000
|Fresh Vegetables|Ginger 1kg|kg|6000
|Fresh Vegetables|Peas fresh 1kg|kg|5000
|Produce|Dried peas 1kg|kg|6000
|Grains & Cereals|Coffee green 1kg|kg|8000
|Grains & Cereals|Coffee processed 1kg|kg|12000
|Grains & Cereals|Tea leaves fresh 1kg|kg|3000
|Fruits|Sugar cane bundle|bundle|3000
|Fruits|Watermelon|pc|5000
|Fruits|Pineapple|pc|3000
|Fruits|Mangoes 1kg|kg|2500
|Fruits|Avocado 1kg|kg|3000
|Fruits|Oranges 1kg|kg|3000
|Fruits|Passion fruits 1kg|kg|5000
|Fruits|Pawpaw|pc|2000
|Fruits|Jackfruit|pc|5000
|Fruits|Lemons 1kg|kg|4000
|Fruits|Tangerines 1kg|kg|4000
|Fuel & Sacks|Charcoal bag 50kg|bag|45000
|Fuel & Sacks|Firewood bundle|bundle|5000
|Fuel & Sacks|Sacks 100kg|pc|5000
""")
        "Electronics & Accessories" -> parse("""
|Chargers & Power|Phone chargers 18W|pc|12000
|Chargers & Power|Fast chargers 33W|pc|25000
|Chargers & Power|Charger cables USB-C 1m|pc|5000
|Chargers & Power|Charger cables micro-USB|pc|4000
|Chargers & Power|Lightning cables|pc|8000
|Chargers & Power|Power banks 10000mAh|pc|50000
|Chargers & Power|Power banks 20000mAh|pc|90000
|Chargers & Power|Wireless chargers|pc|40000
|Audio|Earphones wired|pc|5000
|Chargers & Power|Wireless earbuds TWS|pc|35000
|Audio|Bluetooth headphones|pc|70000
|Audio|Headphones gaming|pc|90000
|Audio|Bluetooth speakers small|pc|30000
|Audio|Bluetooth speakers medium|pc|60000
|Audio|Party speakers 8-inch|pc|250000
|Audio|Subwoofers 12-inch|pc|400000
|Audio|Amplifiers 2-channel|pc|200000
|Audio|Mixers 4-channel|pc|300000
|Audio|Microphones wired|pc|25000
|Chargers & Power|Microphones wireless|pc|120000
|Audio|Megaphones|pc|80000
|TVs & Video|Smart TVs 32-inch|pc|900000
|TVs & Video|Smart TVs 43-inch|pc|1400000
|TVs & Video|TV digital decoders|pc|90000
|TVs & Video|TV wall mounts|pc|25000
|Chargers & Power|HDMI cables 2m|pc|15000
|Chargers & Power|AV cables|pc|5000
|TVs & Video|Antenna TV|pc|15000
|Gadgets & Accessories|Extension 4-way 5m|pc|25000
|Gadgets & Accessories|Extension 6-way|pc|35000
|Solar|Solar panels 100W|pc|250000
|Solar|Solar panels 300W|pc|650000
|Solar|Solar batteries 100Ah|pc|450000
|Solar|Solar inverters 1kVA|pc|600000
|Solar|Solar lights outdoor|pc|25000
|Solar|Solar bulbs|pc|10000
|Torches & Batteries|Torches small|pc|8000
|Torches & Batteries|Torches rechargeable|pc|25000
|Gadgets & Accessories|Batteries AA 4-pack|pkt|2000
|Gadgets & Accessories|Batteries AAA 4-pack|pkt|2000
|Computers & Storage|Memory cards 32GB|pc|25000
|Computers & Storage|Memory cards 64GB|pc|45000
|Computers & Storage|USB flash 64GB|pc|45000
|Computers & Storage|External HDD 1TB|pc|250000
|Computers & Storage|Laptop sleeve|pc|20000
|Chargers & Power|Mouse wireless|pc|25000
|Chargers & Power|Keyboards wireless|pc|40000
|Computers & Storage|USB hubs 4-port|pc|15000
|Computers & Storage|Web cameras HD|pc|60000
|Computers & Storage|Laptop stands|pc|30000
|Computers & Storage|Routers 4G|pc|180000
|Computers & Storage|MiFi devices|pc|120000
|TVs & Video|CCTV cameras|pc|100000
|TVs & Video|CCTV DVR 4-channel|pc|350000
|Home Appliances|Electric kettles|pc|60000
|Home Appliances|Electric irons|pc|45000
|Home Appliances|Flat irons steam|pc|80000
|Home Appliances|Blenders|pc|120000
|Home Appliances|Electric cookers|pc|150000
|Home Appliances|Rice cookers|pc|100000
|Home Appliances|Microwaves|pc|350000
|Home Appliances|Fridges 100L|pc|1200000
|Home Appliances|Fans standing|pc|80000
|Home Appliances|Fans table|pc|40000
|Home Appliances|Water dispensers|pc|250000
|Wearables & Gadgets|Smart watches|pc|80000
|Wearables & Gadgets|Fitness bands|pc|40000
|Chargers & Power|Car chargers|pc|10000
|Wearables & Gadgets|Car Bluetooth kits|pc|25000
|Wearables & Gadgets|Dash cams|pc|150000
|Solar|Calculators solar|pc|15000
|Wearables & Gadgets|Watches assorted|pc|20000
|Wearables & Gadgets|Alarm clocks|pc|15000
""")
        "Fruits & Vegetables" -> parse("""
|Leafy Greens|Dodo (amaranth) bundle|bundle|1000
|Leafy Greens|Nakati bundle|bundle|1000
|Leafy Greens|Sukuma wiki bundle|bundle|1500
|Leafy Greens|Spinach bundle|bundle|1200
|Leafy Greens|Spring onions bundle|bundle|1000
|Leafy Greens|Lettuce head|head|2000
|Fruits|Bananas (sweet) bunch|bunch|15000
|Fruits|Matooke bunch medium|bunch|15000
|Fruits|Matooke bunch large|bunch|25000
|Fruits|Mangoes each|each|500
|Fruits|Avocado each|each|500
|Fruits|Oranges|kg|2000
|Fruits|Passion fruits|kg|3000
|Fruits|Pawpaw|each|1500
|Fruits|Pineapple|each|2000
|Fruits|Watermelon 5kg|each|7000
|Fruits|Jackfruit|each|5000
|Fruits|Lemons|kg|2500
|Fruits|Apples|each|800
|Fruits|Grapes punnet|punnet|5000
|Vegetables|Tomatoes|kg|3000
|Vegetables|Tomatoes crate|crate|45000
|Vegetables|Onions|kg|3500
|Vegetables|Green peppers|kg|2500
|Vegetables|Carrots|kg|2500
|Vegetables|Eggplants|kg|2000
|Vegetables|Cauliflower head|head|3000
|Vegetables|Broccoli head|head|4000
|Vegetables|Cucumber|kg|2000
|Vegetables|Garlic|100g|1000
|Vegetables|Ginger|kg|4000
|Vegetables|Mushrooms|kg|8000
|Root Crops & Tubers|Irish potatoes|kg|1500
|Root Crops & Tubers|Irish potatoes 100kg bag|bag|130000
|Root Crops & Tubers|Sweet potatoes|kg|1500
|Root Crops & Tubers|Cassava fresh|kg|1000
|Root Crops & Tubers|Yams|kg|2000
|Root Crops & Tubers|Arrowroots|each|500
""")
        "Poultry & Eggs" -> parse("""
|Live Birds|Day-old chicks — broiler|chick|3500
|Live Birds|Day-old chicks — layer|chick|3200
|Live Birds|Kienyeji chicks day-old|chick|3000
|Live Birds|Point-of-lay pullets|bird|25000
|Live Birds|Broilers 4 weeks|bird|15000
|Live Birds|Spent layers|bird|12000
|Live Birds|Rooster (cock)|bird|25000
|Live Birds|Turkeys live|bird|80000
|Live Birds|Ducks live|bird|30000
|Live Birds|Guinea fowls live|bird|35000
|Eggs|Eggs tray 30 — medium|tray|12000
|Eggs|Eggs tray 30 — large|tray|14000
|Eggs|Eggs tray 30 — kienyeji|tray|15000
|Eggs|Eggs each|egg|500
|Eggs|Eggs crate (126)|crate|126000
|Feeds|Chick starter 50kg|bag|110000
|Feeds|Growers mash 50kg|bag|105000
|Feeds|Layers mash 50kg|bag|110000
|Feeds|Broiler starter 50kg|bag|120000
|Feeds|Broiler finisher 50kg|bag|115000
|Feeds|Kienyeji mash 50kg|bag|100000
|Feeds|Maize bran 50kg|bag|50000
|Feeds|Shells 50kg|bag|30000
|Feeds|Premix 1kg|kg|12000
|Health & Vaccines|Newcastle (ND) vaccine vial|vial|12000
|Health & Vaccines|Gumboro vaccine vial|vial|15000
|Health & Vaccines|Fowl typhoid vaccine vial|vial|15000
|Health & Vaccines|Poultry vitamins 100g|pack|8000
|Health & Vaccines|Poultry dewormer 100g|pack|6000
|Health & Vaccines|Coccidiostat 100g|pack|8000
|Health & Vaccines|Disinfectant 1L|L|15000
|Equipment|Feeders 3kg|each|12000
|Equipment|Drinkers 3L|each|10000
|Equipment|Brooder bulb|each|10000
|Equipment|Charcoal brooder pot|each|15000
|Equipment|Egg trays|each|500
|Equipment|Egg trays pack of 10|pack|4500
|Equipment|Poultry wire mesh|metre|8000
""")
        "Fish Business" -> parse("""
|Fresh Fish|Tilapia — large|each|8000
|Fresh Fish|Tilapia — medium|each|5000
|Fresh Fish|Tilapia whole|kg|10000
|Fresh Fish|Nile perch whole|kg|12000
|Fresh Fish|Nile perch fillet|kg|18000
|Fresh Fish|Catfish whole|kg|9000
|Fresh Fish|Lungfish (mamba)|kg|8000
|Fresh Fish|Silver fish (mukene)|cup|2000
|Smoked & Dried|Smoked tilapia|each|8000
|Smoked & Dried|Smoked mukene|kg|12000
|Smoked & Dried|Dried Nile perch|kg|15000
|Smoked & Dried|Smoked catfish|kg|14000
|Frozen|Frozen tilapia|kg|9000
|Frozen|Frozen mackerel (scomber)|kg|10000
|Frozen|Horse mackerel|kg|9000
|Frozen|Fish fingers 500g|pack|15000
|Frozen|Frozen prawns|kg|35000
|Supplies & Equipment|Ice blocks|each|500
|Supplies & Equipment|Cold storage hire|per day|10000
|Supplies & Equipment|Insulated fish box|each|25000
|Supplies & Equipment|Fish crates|each|8000
|Supplies & Equipment|Weighing scale|each|25000
|Supplies & Equipment|Fish knives|each|8000
|Supplies & Equipment|Smoking firewood|bundle|3000
""")
        "Bakery & Confectionery" -> parse("""
|Baked Goods|Bread white large|loaf|4000
|Baked Goods|Bread sliced|loaf|4000
|Baked Goods|Bread mini|loaf|2000
|Baked Goods|Buns|each|500
|Baked Goods|Buns packet of 6|packet|3000
|Baked Goods|Cakes whole|each|25000
|Baked Goods|Cake slice|slice|2000
|Baked Goods|Cup cakes|each|1000
|Baked Goods|Queen cakes packet|packet|3000
|Baked Goods|Doughnuts|each|500
|Baked Goods|Cookies 200g|pack|3500
|Baked Goods|Croissants|each|2500
|Baked Goods|Chapati|each|1000
|Baked Goods|Mandazi|each|500
|Baked Goods|Birthday cake medium|each|60000
|Ingredients|Wheat flour 1kg|kg|4500
|Ingredients|Wheat flour 25kg|bag|105000
|Ingredients|Sugar 1kg|kg|3800
|Ingredients|Margarine 500g|pack|7000
|Ingredients|Eggs tray 30|tray|14000
|Ingredients|Fresh milk 1L|L|1500
|Ingredients|Yeast 100g|pack|2500
|Ingredients|Baking powder 100g|pack|1500
|Ingredients|Icing sugar 500g|pack|5000
|Ingredients|Cocoa powder 250g|pack|8000
|Ingredients|Vanilla essence 50ml|bottle|3000
|Ingredients|Food colors set|set|8000
|Ingredients|Salt 500g|pack|500
|Ingredients|Cooking oil 1L|L|8000
|Packaging|Cake boxes|each|1500
|Packaging|Bread wrappers|kg|7000
|Packaging|Paper bags pack of 100|pack|5000
|Packaging|Cake boards|each|1000
|Packaging|Ribbons|metre|500
|Packaging|Birthday candles packet|packet|1000
|Equipment & Services|Baking tins|each|8000
|Equipment & Services|Baking trays|each|10000
|Equipment & Services|Mixing bowls|each|10000
|Equipment & Services|Dough mixer|each|450000
|Equipment & Services|Display shelf|each|150000
|Equipment & Services|Custom cake deposit|each|50000
""")
        "Wholesale & Distribution" -> parse("""
|Beverages Wholesale|Coca-Cola crate of 24|crate|30000
|Beverages Wholesale|Soda crate assorted|crate|32000
|Beverages Wholesale|Rwenzori water crate of 24|crate|15000
|Beverages Wholesale|Beer Nile Special crate|crate|62000
|Beverages Wholesale|Beer Club crate|crate|55000
|Beverages Wholesale|Waragi carton of 12|carton|120000
|Beverages Wholesale|Energy drink carton|carton|60000
|Groceries Wholesale|Sugar 50kg bag|bag|190000
|Groceries Wholesale|Rice Kaiso 25kg|bag|90000
|Groceries Wholesale|Rice Super 25kg|bag|110000
|Groceries Wholesale|Posho 25kg|bag|60000
|Groceries Wholesale|Cooking oil 20L jerrycan|jerrycan|130000
|Groceries Wholesale|Salt carton of 50 packets|carton|35000
|Groceries Wholesale|Beans 100kg bag|bag|380000
|Groceries Wholesale|Wheat flour 50kg|bag|210000
|Household Wholesale|Omo 1kg carton of 12|carton|72000
|Household Wholesale|Bar soap carton of 30|carton|105000
|Household Wholesale|Toilet paper carton of 100 rolls|carton|130000
|Household Wholesale|Matchboxes carton|carton|30000
|Household Wholesale|JIK 500ml carton of 12|carton|45000
|Cosmetics Wholesale|Petroleum jelly carton of 48|carton|168000
|Cosmetics Wholesale|Toothpaste carton of 72|carton|200000
|Cosmetics Wholesale|Lotion 500ml carton of 12|carton|180000
|Distribution Services|Carton repacking|per carton|200
|Distribution Services|Delivery within town|per trip|20000
|Distribution Services|Upcountry delivery|per trip|60000
""")
        "Tailoring & Fashion" -> parse("""
|Fabrics|Kitenge piece|piece|25000
|Fabrics|Ankara 6 yards|piece|40000
|Fabrics|Cotton fabric|metre|8000
|Fabrics|Satin|metre|10000
|Fabrics|Silk fabric|metre|15000
|Fabrics|Linen|metre|12000
|Fabrics|Suiting|metre|25000
|Fabrics|Gomesi fabric set|set|35000
|Fabrics|School uniform fabric|metre|8000
|Fabrics|Shirting|metre|9000
|Fabrics|Curtain fabric|metre|7000
|Fabrics|African print 6 yards|piece|35000
|Threads & Notions|Thread spools|each|1000
|Threads & Notions|Sewing needles packet|packet|1500
|Threads & Notions|Buttons packet|packet|2000
|Threads & Notions|Zips|each|1000
|Threads & Notions|Hooks & eyes packet|packet|1500
|Threads & Notions|Measuring tape|each|2000
|Threads & Notions|Tailoring chalk|each|500
|Threads & Notions|Pins packet|packet|1000
|Threads & Notions|Elastic band|metre|1500
|Threads & Notions|Bias tape|metre|1000
|Threads & Notions|Velcro|metre|2000
|Accessories|Handbags|each|25000
|Accessories|Belts|each|10000
|Accessories|Sunglasses|each|8000
|Accessories|Scarves|each|7000
|Accessories|Caps|each|10000
|Accessories|Bead necklaces|each|5000
|Equipment|Sewing machine — manual|each|350000
|Equipment|Sewing machine — electric|each|750000
|Equipment|Overlock machine|each|1200000
|Equipment|Fabric scissors|each|15000
|Equipment|Machine oil 200ml|bottle|5000
|Equipment|Bobbins packet|packet|3000
|Equipment|Machine needles packet|packet|5000
|Equipment|Ironing board|each|60000
|Equipment|Steam iron|each|120000
|Services|Dress making|per dress|40000
|Services|School uniform sewing|per set|25000
|Services|Gomesi making|per gomesi|60000
|Services|Trouser repair|each|5000
|Services|Zip replacement|each|3000
|Services|Clothes alterations|each|5000
|Services|Embroidery|per item|10000
""")
        "School Canteen / Tuck Shop" -> parse("""
|Snacks|Biscuits packet|packet|1000
|Snacks|Crisps packet|packet|1000
|Snacks|Sweets assorted|each|200
|Snacks|Chewing gum packet|packet|500
|Snacks|Queen cakes|each|500
|Snacks|Doughnuts|each|500
|Snacks|Chapati|each|1000
|Snacks|Rolex|each|2500
|Snacks|Samosa|each|500
|Snacks|Mandazi|each|500
|Snacks|Cake slice|slice|1000
|Snacks|Popcorn packet|packet|500
|Drinks|Bottled water 500ml|bottle|1000
|Drinks|Soda 300ml|bottle|1500
|Drinks|Juice sachet|sachet|500
|Drinks|Milk cup|cup|1000
|Drinks|Cocoa drink cup|cup|1500
|Drinks|Tea cup|cup|1000
|Drinks|Yoghurt 250ml|cup|1500
|Stationery|Pens|each|500
|Stationery|Pencils|each|300
|Stationery|Exercise books|each|800
|Stationery|Rubbers|each|300
|Stationery|Sharpeners|each|300
|Stationery|Rulers|each|500
|Stationery|Geometry sets|each|5000
|Stationery|Crayons packet|packet|2500
|Stationery|Mathematical sets|each|8000
|Lunch Meals|Posho & beans plate|plate|2500
|Lunch Meals|Rice & beans plate|plate|3000
|Lunch Meals|Chips plate|plate|3000
|Lunch Meals|Matooke & beef plate|plate|4000
|Essentials|Toilet paper roll|roll|500
|Essentials|Sanitary pads packet|packet|3000
|Essentials|Soap bar|bar|2000
|Essentials|Toothpaste 100ml|tube|3000
|Essentials|Plasters|each|500
""")
        "Transport & Boda Services" -> parse("""
|Fuel & Lubricants|Petrol|per litre|5000
|Fuel & Lubricants|Engine oil 4-stroke 1L|L|15000
|Fuel & Lubricants|2T oil 1L|L|12000
|Fuel & Lubricants|Gear oil 1L|L|18000
|Fuel & Lubricants|Brake fluid 250ml|bottle|8000
|Fuel & Lubricants|Coolant 1L|L|8000
|Fuel & Lubricants|Chain lube 200ml|bottle|10000
|Spare Parts|Spark plugs|each|5000
|Spare Parts|Brake pads pair|pair|20000
|Spare Parts|Chains|each|25000
|Spare Parts|Sprockets|each|20000
|Spare Parts|Tubes 2.75-17|each|12000
|Spare Parts|Tyres|each|45000
|Spare Parts|Bulbs|each|2000
|Spare Parts|Mirrors pair|pair|10000
|Spare Parts|Horns|each|8000
|Spare Parts|Clutch cables|each|5000
|Spare Parts|Air filters|each|8000
|Spare Parts|Oil filters|each|8000
|Services|Boda ride|per km|1500
|Services|Boda hire|per day|40000
|Services|Delivery within town|per trip|10000
|Services|Motorcycle washing|each|10000
|Services|Tyre repair|each|3000
|Services|Puncture repair|each|3000
|Services|General service|each|25000
|Safety Gear|Helmets|each|60000
|Safety Gear|Gloves pair|pair|10000
|Safety Gear|Reflector jackets|each|15000
|Safety Gear|Rain coats|each|25000
|Safety Gear|Boda boots pair|pair|60000
|Safety Gear|Elbow pads pair|pair|15000
|Safety Gear|Knee pads pair|pair|15000
""")
        else -> emptyList()
    }
}