package com.derycode.deryaccount.catalog

/**
 * BusinessCatalog — the predefined Ugandan product catalogs.
 * 12 business categories (General Shop, Supermarket, Hardware, Pharmacy,
 * Agro-Vet, Butchery, Salon, Stationery, Bar/Restaurant, Mobile Money,
 * Produce & Grains, Electronics) with 800+ common items and typical
 * market prices. Powers the Quick Stock Setup wizard so a new shop can
 * stock its shelves in minutes instead of typing items one by one.
 * NOTE: this file is data, not logic — edit prices/items only.
 */

/**
 * BusinessCatalog — comprehensive predefined item lists per business
 * category so the owner just PICKS items instead of typing them.
 * Prices are typical Ugandan market prices (editable per item).
 * Format: "Name|unit|price" — parsed once at load.
 */
object BusinessCatalog {

    data class CatalogItem(val name: String, val unit: String, val price: Double)

    val CATEGORIES = listOf(
        "General Shop", "Supermarket", "Hardware", "Pharmacy / Drug Shop",
        "Agro-Vet & Farm", "Butchery", "Salon & Cosmetics", "Stationery / Bookshop",
        "Bar & Restaurant", "Mobile Money & Airtime", "Produce & Grains", "Electronics & Accessories"
    )

    private fun parse(block: String): List<CatalogItem> = block.trimIndent().lines()
        .filter { it.isNotBlank() }
        .map { line ->
            val p = line.split("|")
            CatalogItem(p[0].trim(), p[1].trim(), p[2].trim().toDouble())
        }

    fun itemsFor(category: String): List<CatalogItem> = when (category) {
        "General Shop" -> parse("""
            |Sugar 1kg|kg|5000
            |Sugar 2kg|pack|9800
            |Sugar 500g|pack|2600
            |Rice 1kg Kaiso|kg|4500
            |Rice 1kg Super|kg|6500
            |Rice 2kg|pack|9000
            |Rice 5kg|pack|28000
            |Posho 1kg|kg|3500
            |Posho 5kg|pack|16000
            |Millet flour 1kg|kg|4000
            |Wheat flour 1kg|kg|5500
            |Wheat flour 2kg|pack|10500
            |Cooking oil 500ml|btl|4500
            |Cooking oil 1L|btl|8500
            |Cooking oil 3L|jerrican|24000
            |Cooking oil 5L|jerrican|39000
            |Salt 500g|pack|1000
            |Salt 1kg|pack|2000
            |Bread|loaf|4500
            |Bread sliced|loaf|5000
            |Buns|pc|500
            |Baking powder 100g|tin|1500
            |Yeast 100g|pkt|3000
            |Beans 1kg|kg|5000
            |Beans 5kg|tin|24000
            |Groundnuts 1kg|kg|7000
            |Groundnut paste 500g|tin|9000
            |Simsim paste 500g|tin|9000
            |Gnuts roasted 100g|pkt|1500
            |Milk 500ml|pkt|2000
            |Milk 1L|pkt|3800
            |Milk powder 400g|tin|14000
            |Tea leaves 100g|tin|3000
            |Tea leaves 250g|tin|6500
            |Coffee 100g|tin|3500
            |Coffee 250g|tin|8000
            |Cocoa 100g|tin|3000
            |Cocoa 250g|tin|6500
            |Milo 200g|tin|5500
            |Ovaltine 200g|tin|5500
            |Ghee 250g|tin|8000
            |Honey 500g|bottle|15000
            |Curry powder 50g|pack|1000
            |Curry powder 100g|pack|2000
            |Turmeric 50g|pack|1000
            |Mixed spice 50g|pack|1500
            |Royco mchuzi mix 100g|pkt|1500
            |Tomato sauce 300g|btl|2500
            |Chili sauce 300g|btl|2500
            |Vinegar 250ml|btl|2000
            |Sweets assorted|pkt|500
            |Toffees|pkt|500
            |Chewing gum|pkt|500
            |Lollipops|pc|200
            |Biscuits glucose|pkt|1000
            |Biscuits assorted|pkt|1500
            |Cakes snack|pc|1000
            |Doughnuts|pc|500
            |Eggs tray of 30|tray|15000
            |Eggs each|pc|500
            |Margarine 250g|tin|4000
            |Peanut butter 250g|jar|6000
            |Raisins 100g|pkt|2500
            |Corn flakes 250g|box|6500
            |Porridge flour 1kg|pack|5000
            |Soybean flour 1kg|pack|5500
        """)

        "Supermarket" -> parse("""
            |Coca-Cola 300ml|btl|1500
            |Coca-Cola 500ml|btl|2000
            |Coca-Cola 1.5L|btl|4500
            |Coca-Cola 2L|btl|6000
            |Pepsi 300ml|btl|1500
            |Pepsi 500ml|btl|2000
            |Sprite 300ml|btl|1500
            |Fanta 300ml|btl|1500
            |Fanta Orange 500ml|btl|2000
            |Novida 300ml|btl|1500
            |Mountain Dew 500ml|btl|2000
            |Krest 300ml|btl|1500
            |Stoney 300ml|btl|1500
            |Mirinda Fruity 300ml|btl|1500
            |Rwenzori Water 330ml|btl|800
            |Rwenzori Water 500ml|btl|1000
            |Rwenzori Water 1.5L|btl|2500
            |Azure Water 500ml|btl|1000
            |Alive Water 500ml|btl|1000
            |Minute Maid 500ml|pkt|2000
            |Nnunu Juice 500ml|pkt|2000
            |Rocky's Juice 250ml|pkt|1000
            |Bushera 500ml|bottle|2500
            |Milk shake 250ml|pkt|2000
            |Yoghurt 500ml|cup|3500
            |Yoghurt 1L|cup|6500
            |Mala 500ml|cup|3000
            |Red Bull 250ml|can|5000
            |Monster 500ml|can|8000
            |Energy drink assorted|can|3500
            |Chips potato crisps|pkt|1000
            |Crisps assorted|pkt|1500
            |Popcorn 80g|pkt|2000
            |Niknaks|pkt|1000
            |Biscuits assorted|pkt|1500
            |Cookies 200g|pkt|3000
            |Chocolates|pc|2000
            |Bubblegum|pkt|500
            |Juice concentrate 1L|btl|8000
            |Juice concentrate 500ml|btl|4500
            |Wine 750ml|btl|20000
            |Toilet paper 2-ply|roll|1500
            |Toilet paper pack of 10|pkt|14000
            |Facial tissues|pkt|2000
            |Serviettes 100s|pkt|2000
            |Omo 500g|pkt|4500
            |Omo 1kg|pkt|8500
            |Ariel 500g|pkt|5000
            |Bar soap Geisha 800g|bar|5500
            |Bar soap 300g|bar|2500
            |Petro bar soap|bar|3000
            |JIK bleach 500ml|btl|3500
            |Toilet cleaner 500ml|btl|4000
            |Dish soap 500ml|btl|3000
            |Dettol antiseptic 250ml|btl|6000
            |Handwash 250ml|btl|3500
            |Air freshener 300ml|spray|5000
            |Toothpaste 100ml|tube|3000
            |Toothpaste 50ml|tube|2000
            |Toothbrush|pc|1500
            |Mouthwash 250ml|btl|8000
            |Matchbox 10-pack|pkt|1000
            |Candles packet|pkt|2500
            |Batteries AA 2-pack|pkt|1000
            |Batteries D 2-pack|pkt|2000
            |Razor blades packet|pkt|1500
            |Shaving gel 150ml|btl|5000
            |Sanitary pads|pkt|3000
            |Sanitary pads 8s|pkt|4500
            |Baby diapers small pack|pkt|12000
            |Baby diapers jumbo|pkt|32000
            |Baby wipes|pkt|3000
            |Baby cereal 400g|tin|12000
            |Cotton buds 100s|pkt|1500
            |Slippers|pair|5000
            |Socks pair|pair|2000
            |Handkerchief|pc|1000
            |Umbrella|pc|15000
            |Water bottle 1L|pc|5000
            |Flask 1L|pc|25000
        """)

        "Hardware" -> parse("""
            |Cement Tororo 50kg|bag|34000
            |Cement Hima 50kg|bag|35000
            |Iron sheets 28G|pc|52000
            |Iron sheets 30G|pc|45000
            |Iron sheets 32G|pc|38000
            |Iron sheets 24G|pc|65000
            |Ridge caps|pc|15000
            |Roofing nails 3-inch kg|kg|10000
            |Round bars Y12 per length|pc|16000
            |Round bars Y10 per length|pc|12000
            |Round bars Y8 per length|pc|8000
            |Deformed bars 16mm length|pc|24000
            |Square tubes 2-inch|pc|18000
            |Angle bars 2-inch|pc|12000
            |BRC wire mesh A142|roll|45000
            |BRC wire mesh A193|roll|55000
            |Chicken wire mesh|roll|35000
            |Nails 2-inch kg|kg|8000
            |Nails 3-inch kg|kg|8000
            |Nails 4-inch kg|kg|8000
            |Wire nails 1-inch kg|kg|7500
            |Fired bricks|pc|500
            |Cement bricks|pc|700
            |Culvert pipes 2ft|pc|35000
            |Sand trip|trip|250000
            |Murrum trip|trip|150000
            |Hardcore trip|trip|180000
            |Aggregate 3/4 trip|trip|300000
            |Door ordinary|pc|120000
            |Door steel|pc|250000
            |Door frame|pc|60000
            |Window frame steel|pc|80000
            |Door lock mortice|pc|15000
            |Padlock large|pc|10000
            |Padlock small|pc|5000
            |Hinges pair|pair|3000
            |Handles|pc|2000
            |Paint Crown 20L|tin|90000
            |Paint Crown 4L|tin|22000
            |Paint Royale 20L|tin|110000
            |Undercoat 20L|tin|60000
            |Gloss enamel 4L|tin|25000
            |Lacquer 4L|tin|25000
            |Primer 20L|tin|60000
            |Paint brush 3-inch|pc|8000
            |Paint brush 2-inch|pc|5000
            |Roller + tray set|set|15000
            |Putty 1kg|pkt|3000
            |PVC pipe 4-inch 3m|pc|15000
            |PVC pipe 2-inch 3m|pc|8000
            |PVC pipe 1-inch 3m|pc|5000
            |PVC elbows 4-inch|pc|3000
            |PVC elbows 2-inch|pc|1500
            |PVC tees 4-inch|pc|4000
            |GI pipe 1-inch 6m|pc|35000
            |Basin tap|pc|12000
            |Kitchen sink tap|pc|25000
            |Shower head|pc|20000
            |Water tank 1000L|pc|350000
            |Water tank 5000L|pc|1200000
            |Cable 1.5mm roll|roll|95000
            |Cable 2.5mm roll|roll|145000
            |Cable 4mm roll|roll|240000
            |Sockets 13A|pc|5000
            |Switches 5A|pc|4000
            |Switch socket combo|pc|7000
            |Distribution board 8-way|pc|45000
            |MCB 32A|pc|8000
            |Bulb LED 9W|pc|8000
            |Bulb LED 18W|pc|12000
            |Fluorescent tube 4ft|pc|15000
            |Bulb holder|pc|1500
            |Extension cable 4-way|pc|15000
            |Solar panel 100W|pc|250000
            |Solar panel 200W|pc|450000
            |Solar battery 100Ah|pc|450000
            |Inverter 1000W|pc|450000
            |Hoe flat|pc|15000
            |Hoe forked|pc|16000
            |Panga|pc|12000
            |Slashers|pc|15000
            |Crowbar|pc|20000
            |Pick axe|pc|22000
            |Spade|pc|25000
            |Shovel|pc|25000
            |Wheelbarrow|pc|120000
            |Hammer claw|pc|15000
            |Hammer sledge|pc|25000
            |Spirit level 2ft|pc|15000
            |Measuring tape 5m|pc|8000
            |Trowel|pc|10000
            |Trowel pointing|pc|8000
            |Chisel 1-inch|pc|5000
            |File flat 10-inch|pc|6000
            |Pliers combination|pc|10000
            |Pliers nose|pc|8000
            |Adjustable wrench 12-inch|pc|15000
            |Hacksaw + blades|set|20000
            |Hand saw|pc|20000
            |Try square|pc|5000
            |Welding rods 2.5mm kg|kg|12000
            |Welding rods 3.15mm kg|kg|12000
            |Gloves leather pair|pair|8000
            |Gum boots|pair|25000
            |Overall|pc|25000
            |Dust coat|pc|20000
            |Safety helmet|pc|15000
            |Mask N95|pc|2000
            |Ropes sisal 20m|roll|8000
            |Tarpaulin 5x6m|pc|40000
        """)

        "Pharmacy / Drug Shop" -> parse("""
            |Panadol strip|strip|2000
            |Panadol Extra strip|strip|2500
            |Panadol syrup 60ml|btl|4000
            |Panadol infant syrup|btl|4500
            |Coartem pack|pack|5000
            |Coartem 20/120|pack|5500
            |Artemether injection|vial|5000
            |Quinine injection|vial|4000
            |Quinine tablets strip|strip|2000
            |Amoxicillin 250mg pack|pack|3000
            |Amoxicillin 500mg pack|pack|4000
            |Ampiclox capsules|pack|5000
            |Ciprofloxacin 500 strip|strip|2500
            |Metronidazole strip|strip|1500
            |Metronidazole syrup|btl|4000
            |Azithromycin pack|pack|6000
            |Cephalexin capsules|pack|4500
            |Gentamicin injection|vial|3000
            |Penicillin injection|vial|3500
            |ORS sachets pack|pack|2000
            |Zinc tablets pack|pack|2500
            |Ibuprofen strip|strip|1500
            |Ibuprofen syrup 100ml|btl|5000
            |Diclofenac strip|strip|1500
            |Diclofenac injection|vial|2500
            |Aspirin strip|strip|1000
            |Indocid strip|strip|2000
            |Antacid bottle|btl|5000
            |Antacid tablets|pkt|3000
            |Omeprazole pack|pack|4000
            |Buscopan strip|strip|3000
            |Chlorpheniramine strip|strip|1500
            |Piriton strip|strip|1500
            |Cetirizine strip|strip|2000
            |Loratadine strip|strip|2000
            |Prednisolone strip|strip|2000
            |Dexamethasone strip|strip|2500
            |Diazepam strip|strip|2000
            |Carbamazepine pack|pack|5000
            |Phenobarbitone strip|strip|2000
            |Amitriptyline strip|strip|2500
            |Fluconazole pack|pack|5000
            |Clotrimazole cream|tube|5000
            |Miconazole cream|tube|5000
            |Hydrocortisone cream|tube|6000
            |Betamethasone cream|tube|6000
            |Calamine lotion|btl|4000
            |Cough syrup 100ml|btl|6000
            |Cough lozenges|pkt|2000
            |Salbutamol syrup|btl|5000
            |Ventolin inhaler|pc|25000
            |Cough linctus|btl|5000
            |Multivitamin tablets|pack|5000
            |Vitamin C pack|pack|3000
            |Vitamin B-complex|pack|3500
            |Iron supplements pack|pack|4000
            |Folic acid strip|strip|1500
            |Ferrous sulphate pack|pack|3000
            |Calcium tablets|pkt|4000
            |Zincovit syrup|btl|6000
            |Omega 3 capsules|pkt|8000
            |Insulin vial|vial|45000
            |Metformin pack|pack|4000
            |Glibenclamide strip|strip|2000
            |Amlodipine strip|strip|2500
            |Nifedipine strip|strip|2500
            |Atenolol strip|strip|2500
            |Losartan pack|pack|8000
            |Enalapril strip|strip|3000
            |Furosemide strip|strip|2000
            |HCTZ strip|strip|1500
            |Cotton wool 100g|roll|2000
            |Cotton wool 500g|roll|7000
            |Bandage roll|roll|2000
            |Bandage crepe|roll|4000
            |Plaster roll|roll|1500
            |Plaster strips|pkt|2000
            |Gauze swabs|pkt|3000
            |Surgical spirit 250ml|btl|3000
            |Surgical spirit 500ml|btl|5500
            |Surgical gloves pair|pair|1000
            |Examination gloves box|box|15000
            |Syringes 5ml|pc|500
            |Syringes 2ml|pc|400
            |Syringes 10ml|pc|700
            |Needles 21G packet|pkt|2000
            |Needles 23G packet|pkt|2000
            |IV giving set|pc|2500
            |IV cannula 18G|pc|2000
            |Malaria test kit RDT|pc|2000
            |Typhoid test kit|pc|3000
            |Pregnancy test kit|pc|3000
            |Urine test strips|pkt|5000
            |Blood glucose strips|pkt|15000
            |Digital thermometer|pc|10000
            |BP machine manual|pc|120000
            |Stethoscope|pc|50000
            |Weighing scale|pc|60000
            |Condoms packet|pkt|2000
            |Betadine 100ml|btl|5000
            |Hydrogen peroxide 100ml|btl|3000
            |Eye drops chloramphenicol|btl|5000
            |Eye drops dexamethasone|btl|8000
            |Ear drops|btl|6000
            |Nasal spray|btl|10000
            |Antiseptic soap|bar|3000
            |Hand sanitizer 250ml|btl|4000
            |Face masks box of 50|box|10000
            |First aid kit|set|40000
        """)

        "Agro-Vet & Farm" -> parse("""
            |Maize seed Longe 5 1kg|kg|6000
            |Maize seed Longe 10 1kg|kg|8000
            |Bean seed 1kg|kg|8000
            |Bean seed K132 1kg|kg|9000
            |Sorghum seed 1kg|kg|5000
            |Millet seed 1kg|kg|6000
            |Rice seed 1kg|kg|6000
            |Sunflower seed 1kg|kg|7000
            |Soybean seed 1kg|kg|7500
            |Groundnut seed 1kg|kg|9000
            |DAP 50kg|bag|180000
            |NPK 17-17-17 50kg|bag|190000
            |NPK 25-5-5 50kg|bag|185000
            |Urea 50kg|bag|170000
            |CAN 50kg|bag|160000
            |TSP 50kg|bag|175000
            |MOP 50kg|bag|200000
            |Manure bag|bag|10000
            |Lime agricultural 50kg|bag|45000
            |Foliar feed 1L|btl|15000
            |Foliar feed 250ml|btl|6000
            |Sprayer 16L knapsack|pc|120000
            |Sprayer 8L manual|pc|60000
            |Sprayer parts lance|pc|8000
            |Sprayer nozzles|pkt|3000
            |Spray boots|pair|25000
            |Overalls agro|pc|25000
            |Dudu Acelle 100ml|btl|15000
            |Rocket 100ml|btl|12000
            |Weed Master 100ml|btl|10000
            |Roundup 1L|btl|30000
            |Gramoxone 1L|btl|28000
            |Lazer 100ml|btl|12000
            |Dudufen 100g|pkt|10000
            |Mancozeb 1kg|pkt|18000
            |Copper oxychloride 1kg|pkt|15000
            |Ridomil 500g|pkt|45000
            |Actara 50g|pkt|20000
            |Virtako 40g|pkt|15000
            |Ambush 100ml|btl|12000
            |Dipterex 500g|pkt|10000
            |Acaramoth 100ml|btl|15000
            |Cattle dip 1L|btl|25000
            |Albendazole 500ml|btl|25000
            |Ivermectin injection 50ml|btl|20000
            |Oxytetracycline 500ml|btl|30000
            |Penstripp 500ml|btl|35000
            |Liver block 50kg|bag|130000
            |Growers mash 50kg|bag|125000
            |Layers mash 50kg|bag|130000
            |Chick starter 50kg|bag|140000
            |Broiler starter 50kg|bag|145000
            |Broiler finisher 50kg|bag|140000
            |Poultry vitamins 100g|pkt|8000
            |Poultry vaccination ND vial|vial|10000
            |Gumboro vaccine vial|vial|12000
            |Fowl typhoid vaccine|vial|10000
            |Day-old chicks|pc|3500
            |Point of lay pullets|pc|25000
            |Egg trays|tray|500
            |Feeders poultry|pc|12000
            |Drinkers poultry|pc|10000
            |Poultry cages 120-bird|pc|900000
            |Brooder lamps|pc|10000
            |Pig feed 50kg|bag|135000
            |Pig creep feed 50kg|bag|150000
            |Rabbit pellets 50kg|bag|140000
            |Rabbit cages|pc|50000
            |Dog food 20kg|bag|130000
            |Cat food 10kg|bag|100000
            |Pet vaccines|vial|15000
            |Dewormers dogs|pkt|8000
            |Grafting tools set|set|10000
            |Pruning shears|pc|12000
            |Secateurs|pc|10000
            |Budding tape|roll|2000
            |Shade net 3x5m|pc|25000
            |Mulch paper 50m|roll|45000
            |Drip lines 100m|roll|90000
            |Irrigation pipes 2-inch 30m|roll|120000
            |Water pumps 5.5HP|pc|1200000
            |Fish feed 50kg|bag|180000
            |Fish pond liners|pc|250000
        """)

        "Butchery" -> parse("""
            |Beef boneless|kg|16000
            |Beef with bones|kg|14000
            |Beef mince|kg|17000
            |Goat meat|kg|18000
            |Mutton|kg|18000
            |Pork|kg|15000
            |Pork ribs|kg|16000
            |Chicken whole fresh|kg|14000
            |Chicken whole frozen|kg|12000
            |Chicken parts|kg|15000
            |Chicken breasts|kg|18000
            |Turkey|kg|25000
            |Duck|kg|20000
            |Liver beef|kg|10000
            |Liver chicken|kg|12000
            |Kidney|kg|8000
            |Tripe|kg|8000
            |Ox tail|kg|12000
            |Ox tongue|kg|12000
            |Tendon|kg|7000
            |Sausages raw|kg|18000
            |Sausages smoked|kg|20000
            |Smokies pack of 10|pkt|8000
            |Bacon 500g|pkt|15000
            |Ham 500g|pkt|15000
            |Salami 500g|pkt|18000
            |Smoked meat|kg|20000
            |Dried meat strips|kg|25000
            |Suet|kg|5000
            |Bones for soup|kg|3000
            |Offal mixed|kg|6000
            |Chicken gizzards|kg|9000
            |Chicken feet|kg|5000
            |Chicken heads|kg|4000
            |Eggs tray 30|tray|15000
            |Charcoal bag|bag|40000
            |Butcher knives|pc|15000
            |Cutting boards|pc|20000
            |Meat hooks|pc|5000
            |Cling film 300m|roll|15000
            |Cooler box large|pc|150000
        """)

        "Salon & Cosmetics" -> parse("""
            |Hair relaxer 500ml|tin|15000
            |Hair relaxer 1L|tin|25000
            |Dark & Lovely kit|kit|20000
            |Motions relaxer kit|kit|25000
            |Shampoo 500ml|btl|12000
            |Shampoo 1L|btl|20000
            |Conditioner 500ml|btl|12000
            |Conditioner 1L|btl|20000
            |Hair food 250g|tin|8000
            |Hair food 500g|tin|14000
            |Petroleum jelly 250g|tin|4000
            |Petroleum jelly 100g|tin|2000
            |Coconut oil 250ml|btl|10000
            |Castor oil 250ml|btl|8000
            |Olive oil 250ml|btl|10000
            |Shea butter 250g|jar|10000
            |Cocoa butter 250g|jar|10000
            |Body lotion 500ml|btl|10000
            |Body lotion 250ml|btl|6000
            |Body butter 250g|jar|12000
            |Body wash 500ml|btl|8000
            |Body soap bar|bar|3000
            |Herbal soap|bar|3500
            |Face wash 150ml|tube|8000
            |Face cream 100ml|jar|10000
            |Face mask 100g|pkt|8000
            |Scrubs 250g|pkt|10000
            |Toners 200ml|btl|10000
            |Foundation 30ml|btl|12000
            |Concealer|pc|8000
            |Powder compact|pc|8000
            |Face powder jar|jar|6000
            |Blush|pc|7000
            |Bronzer|pc|8000
            |Highlighter|pc|10000
            |Contour kit|kit|20000
            |Eyebrow pencil|pc|3000
            |Eyeliner|pc|5000
            |Mascara|pc|8000
            |Eyeshadow palette|pc|15000
            |Lipstick|pc|5000
            |Lip gloss|pc|4000
            |Lip liner|pc|3000
            |Lip balm|pc|2000
            |Makeup brushes set|set|25000
            |Makeup sponges|pkt|3000
            |Makeup remover 200ml|btl|8000
            |Makeup mirror|pc|8000
            |False eyelashes|pair|5000
            |Nail polish|pc|3000
            |Nail polish remover 250ml|btl|4000
            |Nail files|pc|1000
            |Nail clippers|pc|3000
            |Nail extensions kit|kit|25000
            |Acrylic nail kit|kit|50000
            |Cuticle oil|btl|5000
            |Hand cream 100ml|tube|7000
            |Cuticle pusher|pc|2000
            |Weave human hair|pc|50000
            |Weave synthetic|pc|25000
            |Braids packet|pkt|15000
            |Braids jumbo|pkt|20000
            |Crochet hair|pkt|25000
            |Wig lace front|pc|120000
            |Wig ordinary|pc|50000
            |Hair closure|pc|30000
            |Hair bundles|pkt|70000
            |Hair coloring kit|kit|25000
            |Hair bleach kit|kit|30000
            |Hair toner|btl|15000
            |Hair spray 250ml|btl|10000
            |Hair mousse 250ml|btl|10000
            |Hair gel 250g|jar|5000
            |Hair gel 500g|jar|8000
            |Hair oil 200ml|btl|8000
            |Heat protectant|btl|12000
            |Hair masks 500ml|btl|15000
            |Combs assorted|pc|2000
            |Combs afro|pc|3000
            |Hair brushes|pc|5000
            |Rat tail comb|pc|2000
            |Hair pins packet|pkt|1000
            |Bobby pins packet|pkt|1000
            |Hair clips|pkt|2000
            |Headbands|pc|3000
            |Scarves|pc|5000
            |Durags|pc|5000
            |Bonnets satin|pc|8000
            |Hair dryers|pc|80000
            |Curling irons|pc|60000
            |Straighteners flat|pc|70000
            |Hair clippers|pc|80000
            |Trimmers|pc|60000
            |Clipper blades|pc|15000
            |Razor blades packet|pkt|1500
            |Shaving cream|btl|5000
            |Aftershave 250ml|btl|10000
            |Beard oil 50ml|btl|12000
            |Beard kit|kit|30000
            |Barber capes|pc|15000
            |Neck strips|roll|3000
            |Towels salon|pc|8000
            |Mirrors wall|pc|25000
            |Salon chairs|pc|300000
            |Hair washing basin|pc|150000
        """)

        "Stationery / Bookshop" -> parse("""
            |Exercise book 32pg sq|pc|1000
            |Exercise book 48pg sq|pc|1500
            |Exercise book 64pg sq|pc|2000
            |Exercise book 96pg sq|pc|2500
            |Counter book 2-quire|pc|7000
            |Counter book 4-quire|pc|12000
            |Hardcover book 2-quire|pc|15000
            |Hardcover book 4-quire|pc|25000
            |Ruled refills packet|pkt|3000
            |Plain refills packet|pkt|3000
            |Squared paper packet|pkt|3000
            |Graph paper 5mm|pkt|3000
            |Pens blue Bic|pc|500
            |Pens black Bic|pc|500
            |Pens red Bic|pc|500
            |Pens green Bic|pc|500
            |Gel pens|pc|1000
            |Ballpoint assorted box|box|12000
            |Pencils HB|pc|500
            |Pencils 2B|pc|500
            |Pencils colored box of 12|box|8000
            |Pencil sharpeners|pc|500
            |Erasers|pc|500
            |Rulers 30cm|pc|1000
            |Rulers 15cm|pc|500
            |Set squares|pc|1500
            |Protractors|pc|1000
            |Compass pencil|pc|1500
            |Geometry box set|set|3000
            |Calculators basic|pc|15000
            |Calculators scientific|pc|45000
            |Crayons 12-pack|pkt|3000
            |Crayons 24-pack|pkt|5000
            |Colored pencils 12|pkt|4000
            |Felt pens 12|pkt|5000
            |Markers whiteboard|pc|2000
            |Markers permanent|pc|2000
            |Highlighters|pc|2000
            |Highlighters set of 4|set|6000
            |Glue sticks 20g|pc|2000
            |Glue bottle 100ml|btl|3000
            |Paper glue 250ml|btl|4000
            |Staplers|pc|8000
            |Staples pins|pkt|1500
            |Staple remover|pc|3000
            |Paper clips 100s|box|2000
            |Push pins 100s|box|2000
            |Drawing pins|box|2000
            |Rubber bands 100g|pkt|2000
            |A4 paper ream 70gsm|ream|25000
            |A4 paper ream 80gsm|ream|28000
            |A3 paper ream|ream|55000
            |Photocopy paper rim|ream|25000
            |Files folders A4|pc|2000
            |Box files|pc|12000
            |Lever arch files|pc|18000
            |Expanding files|pc|15000
            |Display books A4|pc|8000
            |Ring binders 2-ring|pc|6000
            |Plastic sleeves A4|pkt|3000
            |Punch 2-hole|pc|8000
            |Scissors small|pc|2000
            |Scissors large|pc|4000
            |Cutting mats A4|pc|10000
            |Craft knives|pc|3000
            |Tape rolls clear|roll|1500
            |Tape rolls double-sided|roll|3000
            |Masking tape|roll|2500
            |Duct tape|roll|5000
            |Envelopes A4 white|pkt|3000
            |Envelopes A5 brown|pkt|2500
            |Envelopes padded|pc|1000
            |Bubble wrap 1m|roll|5000
            |School bags|pc|30000
            |Lunch boxes|pc|10000
            |Water bottles school|pc|8000
            |Maths sets primary|set|5000
            |Charts educational|pc|5000
            |Globe|pc|25000
            |Chalk box of 100|box|5000
            |Whiteboard markers pack|pkt|6000
            |Whiteboard dusters|pc|2000
            |Blackboard paint|tin|25000
            |Index cards 100s|pkt|3000
            |Sticky notes 100s|pkt|2500
            |Notebooks spiral A5|pc|5000
            |Notebooks hard A5|pc|6000
            |Diaries 2026|pc|15000
            |Planners 2026|pc|18000
            |Certificates A4 50s|pkt|15000
            |Lamination pouches A4 50s|pkt|12000
            |Ink pads|pc|3000
            |Rubber stamps custom|pc|25000
            |Date stamps|pc|20000
        """)

        "Bar & Restaurant" -> parse("""
            |Beer Nile Special|btl|6000
            |Beer Nile Gold|btl|5000
            |Beer Club|btl|5000
            |Beer Club Pilsner|btl|5000
            |Beer Bell Lager|btl|5000
            |Beer Bell Ale|btl|5500
            |Beer Guinness|btl|5500
            |Beer Tusker Lite|btl|5500
            |Beer Tusker Malt|btl|5500
            |Beer Senator|btl|4000
            |Beer Ngoma|btl|4000
            |Beer Eagle Lager|btl|4500
            |Beer canned assorted|can|5500
            |Waragi 350ml|btl|5000
            |Waragi 750ml|btl|15000
            |Konyagi 350ml|btl|5000
            |Bond 7 750ml|btl|25000
            |Uganda Waragi coffee 350ml|btl|5500
            |Vodka 750ml|btl|20000
            |Gin 750ml|btl|18000
            |Rum 750ml|btl|22000
            |Whisky 750ml|btl|35000
            |Brandy 750ml|btl|25000
            |Wine red 750ml|btl|20000
            |Wine white 750ml|btl|20000
            |Wine sweet 1L|btl|15000
            |Sparkling wine|btl|45000
            |Soda 500ml assorted|btl|2500
            |Soda 300ml assorted|btl|1500
            |Energy drink can|can|4000
            |Bottled water 500ml|btl|1000
            |Juice fresh glass|glass|3000
            |Juice packed 500ml|pkt|2000
            |Cocktail assorted|glass|10000
            |Rice plate dish|plate|5000
            |Posho & beans|plate|4000
            |Posho & beef|plate|7000
            |Matooke & beef|plate|8000
            |Matooke & beans|plate|5000
            |Matooke & groundnut|plate|6000
            |Chips plain|plate|5000
            |Chips & chicken|plate|10000
            |Chips & sausage|plate|7000
            |Chips & beef|plate|8000
            |Roast chicken whole|pc|20000
            |Roast chicken portion|plate|12000
            |Grilled fish tilapia|plate|15000
            |Fish stew|plate|12000
            |Beef stew|plate|8000
            |Goat stew|plate|10000
            |Pork kilo|kg|15000
            |Pork dish|plate|10000
            |Muchomo assorted|plate|8000
            |Rolex plain|pc|3000
            |Rolex with sausage|pc|4000
            |Chapati plain|pc|1000
            |Chapati with eggs|pc|2500
            |Samosa|pc|500
            |Mandazi|pc|500
            |Bananas fried|plate|2000
            |Kikomando|plate|3500
            |Salad plate|plate|3000
            |Soup chicken|bowl|4000
            |Tea cup|cup|1000
            |Coffee cup|cup|2000
            |African tea|cup|1500
            |Chocolate drink|cup|2500
            |Milk tea|cup|1500
            |Porridge cup|cup|1000
            |Breakfast eggs|plate|5000
            |Toast bread|plate|3000
            |Beans plain|bowl|3000
            |Gnuts sauce|bowl|4000
            |Spaghetti plate|plate|5000
            |Pizza slice|pc|5000
            |Burger|pc|10000
            |Shawarma|pc|8000
            |Milkshake|glass|6000
            |Smoothie assorted|glass|6000
            |Ice cream scoop|pc|2000
            |Cigarettes pack|pkt|10000
            |Matchbox|pc|200
            |Sufuria hire|pc|2000
        """)

        "Mobile Money & Airtime" -> parse("""
            |MTN airtime 500|pc|500
            |MTN airtime 1000|pc|1000
            |MTN airtime 2000|pc|2000
            |MTN airtime 5000|pc|5000
            |MTN airtime 10000|pc|10000
            |MTN airtime 20000|pc|20000
            |Airtel airtime 500|pc|500
            |Airtel airtime 1000|pc|1000
            |Airtel airtime 2000|pc|2000
            |Airtel airtime 5000|pc|5000
            |Airtel airtime 10000|pc|10000
            |Airtel airtime 20000|pc|20000
            |Lycamobile airtime 1000|pc|1000
            |Data bundle 1.5GB|pc|3000
            |Data bundle 7GB|pc|10000
            |Data bundle 15GB|pc|20000
            |Data bundle 30GB|pc|40000
            |Data bundle daily 1GB|pc|1000
            |WhatsApp bundle daily|pc|500
            |WhatsApp bundle weekly|pc|3000
            |WhatsApp bundle monthly|pc|12000
            |YouTube bundle weekly|pc|5000
            |Social media bundle daily|pc|1000
            |Minutes bundle MTN 60min|pc|3000
            |Minutes bundle Airtel 60min|pc|3000
            |International minutes 50min|pc|20000
            |MTN SIM card|pc|2000
            |Airtel SIM card|pc|2000
            |SIM replacement|pc|5000
            |SIM registration|pc|1000
            |MoMo withdrawal fee|pc|500
            |Airtel Money withdrawal fee|pc|500
            |Send money fee|pc|500
            |Deposit to wallet|pc|0
            |MTN MoMo cash-out 10k|pc|400
            |MoMo agent commission earned|pc|0
            |Phone chargers|pc|8000
            |Charger cables USB-C|pc|5000
            |Charger cables micro-USB|pc|4000
            |Charger cables iPhone|pc|8000
            |Power banks 10000mAh|pc|50000
            |Earphones wired|pc|5000
            |Earphones wireless|pc|30000
            |Bluetooth speakers|pc|45000
            |Phone covers assorted|pc|3000
            |Screen protectors|pc|2000
            |Phone holders|pc|5000
            |Memory cards 16GB|pc|15000
            |Memory cards 32GB|pc|25000
            |USB flash 32GB|pc|30000
            |Phone screen repair|pc|30000
            |Software flashing|pc|15000
            |Phone unlocking|pc|10000
            |Accessories assorted|pc|5000
        """)

        "Produce & Grains" -> parse("""
            |Maize grain 1kg|kg|1500
            |Maize bran 50kg|bag|40000
            |Maize flour 1kg|kg|3500
            |Maize flour 10kg|bag|30000
            |Maize flour 25kg|bag|70000
            |Maize cob 100kg bag|bag|120000
            |Beans yellow 1kg|kg|5000
            |Beans red 1kg|kg|5000
            |Beans mixed 100kg|bag|480000
            |Soybeans 1kg|kg|5000
            |Groundnuts shells 1kg|kg|5000
            |Groundnuts clean 1kg|kg|7000
            |Simsim 1kg|kg|9000
            |Sunflower seeds 1kg|kg|6000
            |Rice paddy 1kg|kg|2500
            |Rice milled Kaiso 25kg|bag|110000
            |Rice milled Super 25kg|bag|155000
            |Millet 1kg|kg|4000
            |Sorghum 1kg|kg|3000
            |Cassava fresh 1kg|kg|1000
            |Cassava flour 1kg|kg|3000
            |Sweet potatoes 1kg|kg|1500
            |Irish potatoes 1kg|kg|2000
            |Bananas bunch medium|bunch|15000
            |Bananas bunch large|bunch|25000
            |Matooke 1kg|kg|2000
            |Bananas sweet 1kg|kg|2000
            |Tomatoes 1kg|kg|3000
            |Onions 1kg|kg|4000
            |Cabbage head|pc|3000
            |Carrots 1kg|kg|4000
            |Green peppers 1kg|kg|5000
            |Dodo 1kg|kg|2000
            |Nakati 1kg|kg|2000
            |Sukuma wiki 1kg|kg|2000
            |Eggplants 1kg|kg|3000
            |Cauliflower head|pc|4000
            |Broccoli head|pc|4000
            |Cucumber 1kg|kg|2500
            |Garlic 100g|pkt|2000
            |Ginger 1kg|kg|6000
            |Peas fresh 1kg|kg|5000
            |Dried peas 1kg|kg|6000
            |Coffee green 1kg|kg|8000
            |Coffee processed 1kg|kg|12000
            |Tea leaves fresh 1kg|kg|3000
            |Sugar cane bundle|bundle|3000
            |Watermelon|pc|5000
            |Pineapple|pc|3000
            |Mangoes 1kg|kg|2500
            |Avocado 1kg|kg|3000
            |Oranges 1kg|kg|3000
            |Passion fruits 1kg|kg|5000
            |Pawpaw|pc|2000
            |Jackfruit|pc|5000
            |Lemons 1kg|kg|4000
            |Tangerines 1kg|kg|4000
            |Charcoal bag 50kg|bag|45000
            |Firewood bundle|bundle|5000
            |Sacks 100kg|pc|5000
        """)

        "Electronics & Accessories" -> parse("""
            |Phone chargers 18W|pc|12000
            |Fast chargers 33W|pc|25000
            |Charger cables USB-C 1m|pc|5000
            |Charger cables micro-USB|pc|4000
            |Lightning cables|pc|8000
            |Power banks 10000mAh|pc|50000
            |Power banks 20000mAh|pc|90000
            |Wireless chargers|pc|40000
            |Earphones wired|pc|5000
            |Wireless earbuds TWS|pc|35000
            |Bluetooth headphones|pc|70000
            |Headphones gaming|pc|90000
            |Bluetooth speakers small|pc|30000
            |Bluetooth speakers medium|pc|60000
            |Party speakers 8-inch|pc|250000
            |Subwoofers 12-inch|pc|400000
            |Amplifiers 2-channel|pc|200000
            |Mixers 4-channel|pc|300000
            |Microphones wired|pc|25000
            |Microphones wireless|pc|120000
            |Megaphones|pc|80000
            |Smart TVs 32-inch|pc|900000
            |Smart TVs 43-inch|pc|1400000
            |TV digital decoders|pc|90000
            |TV wall mounts|pc|25000
            |HDMI cables 2m|pc|15000
            |AV cables|pc|5000
            |Antenna TV|pc|15000
            |Extension 4-way 5m|pc|25000
            |Extension 6-way|pc|35000
            |Solar panels 100W|pc|250000
            |Solar panels 300W|pc|650000
            |Solar batteries 100Ah|pc|450000
            |Solar inverters 1kVA|pc|600000
            |Solar lights outdoor|pc|25000
            |Solar bulbs|pc|10000
            |Torches small|pc|8000
            |Torches rechargeable|pc|25000
            |Batteries AA 4-pack|pkt|2000
            |Batteries AAA 4-pack|pkt|2000
            |Memory cards 32GB|pc|25000
            |Memory cards 64GB|pc|45000
            |USB flash 64GB|pc|45000
            |External HDD 1TB|pc|250000
            |Laptop sleeve|pc|20000
            |Mouse wireless|pc|25000
            |Keyboards wireless|pc|40000
            |USB hubs 4-port|pc|15000
            |Web cameras HD|pc|60000
            |Laptop stands|pc|30000
            |Routers 4G|pc|180000
            |MiFi devices|pc|120000
            |CCTV cameras|pc|100000
            |CCTV DVR 4-channel|pc|350000
            |Electric kettles|pc|60000
            |Electric irons|pc|45000
            |Flat irons steam|pc|80000
            |Blenders|pc|120000
            |Electric cookers|pc|150000
            |Rice cookers|pc|100000
            |Microwaves|pc|350000
            |Fridges 100L|pc|1200000
            |Fans standing|pc|80000
            |Fans table|pc|40000
            |Water dispensers|pc|250000
            |Smart watches|pc|80000
            |Fitness bands|pc|40000
            |Car chargers|pc|10000
            |Car Bluetooth kits|pc|25000
            |Dash cams|pc|150000
            |Calculators solar|pc|15000
            |Watches assorted|pc|20000
            |Alarm clocks|pc|15000
        """)

        else -> emptyList()
    }
}
