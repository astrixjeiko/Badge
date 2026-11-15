package com.astrix.badge

// One emoji per line: the emoji, then the words people might search for.
// To add one, add a line anywhere in the list.
private val EMOJI_DATA = """
🍔 burger hamburger fast food
🍟 fries fast food
🍕 pizza
🌭 hotdog hot dog
🌮 taco mexican
🌯 burrito wrap
🥪 sandwich lunch
🥗 salad healthy
🍝 pasta spaghetti noodles
🍜 ramen noodles soup
🍲 stew pot hot pot
🍛 curry rice
🍣 sushi japanese
🍱 bento lunch box
🍚 rice meal
🍗 chicken drumstick fried
🍖 meat
🥩 steak beef
🥓 bacon breakfast
🍳 egg breakfast cooking
🥞 pancakes breakfast
🧇 waffle breakfast
🍞 bread bakery
🥐 croissant bakery pastry
🥖 baguette bread
🧀 cheese
🥚 egg
🥛 milk dairy
☕ coffee cafe tea hot drink
🍵 tea matcha green tea
🧋 milk tea bubble tea boba
🥤 soda drink cup juice
🧃 juice box drink
🍺 beer drinks bar
🍻 beers cheers bar drinks
🍷 wine drinks
🍸 cocktail drinks bar
🍹 tropical drink cocktail
🥂 champagne cheers celebration
🍾 champagne bottle celebration
🍦 ice cream soft serve dessert
🍨 ice cream dessert
🍰 cake dessert slice
🎂 birthday cake celebration
🧁 cupcake dessert
🍩 donut doughnut snack
🍪 cookie snack
🍫 chocolate candy snack
🍬 candy sweets
🍿 popcorn movie snack
🥜 peanuts nuts snack
🍎 apple fruit
🍌 banana fruit
🍇 grapes fruit
🍉 watermelon fruit
🍓 strawberry fruit
🥑 avocado
🥕 carrot vegetable groceries
🥦 broccoli vegetables groceries
🌽 corn
🛒 groceries shopping cart supermarket market
🧺 basket laundry picnic
😋 yummy tasty delicious
🚗 car transport vehicle drive
🚕 taxi cab ride
🚙 suv car vehicle
🚌 bus transport commute
🚎 trolleybus bus commute
🚐 van minibus jeepney
🚓 police car
🚑 ambulance emergency
🚒 fire truck
🚚 truck delivery moving
🚛 lorry truck delivery
🏍️ motorcycle motorbike
🛵 scooter moped
🚲 bicycle bike cycling
🛴 kick scooter
🚆 train rail commute
🚇 metro subway mrt lrt
🚈 light rail
🚊 tram
🚄 bullet train fast
✈️ airplane plane flight travel
🛫 departure flight takeoff
🛬 arrival flight landing
🚢 ship boat ferry cruise
⛵ sailboat boat
🛥️ boat yacht
🚁 helicopter
🚀 rocket
⛽ fuel gas gasoline petrol station
🚦 traffic light
🚧 construction road
🅿️ parking
🛣️ highway road toll
🛞 wheel tire
🔧 wrench repair tools maintenance
🔩 nut bolt hardware
🛍️ shopping bags mall
🎁 gift present birthday
👕 shirt tshirt clothes clothing
👖 jeans pants clothes
👗 dress clothes fashion
👠 high heels shoes
👟 sneakers shoes running
👜 handbag purse bag
🎒 backpack school bag
🧢 cap hat
👓 glasses eyewear
🕶️ sunglasses
💍 ring jewelry
⌚ watch
💄 lipstick makeup beauty
💅 nail polish manicure beauty
💇 haircut salon barber
🧴 lotion skincare toiletries
🧼 soap cleaning hygiene
🪥 toothbrush dental hygiene
🧻 toilet paper tissue
🏠 house home rent
🏡 house garden home
🏢 office building apartment
🏦 bank
🏧 atm cash withdrawal
💡 light bulb electricity power bill
🔌 plug electricity power
🚿 shower water bill
🚰 water tap utilities
🔥 fire gas heating
📶 wifi internet signal
📡 antenna satellite internet
📞 phone call telephone
☎️ telephone landline
📱 phone mobile load data
💻 laptop computer
🖥️ desktop computer
🖨️ printer
🛋️ couch sofa furniture
🛏️ bed bedroom furniture
🪑 chair furniture
🚪 door
🧹 broom cleaning chores
🧽 sponge cleaning
🪣 bucket cleaning
🔑 key rent
📦 package parcel delivery box
📬 mailbox mail post
💰 money bag savings cash
💵 dollar cash bill money
💴 yen money
💶 euro money
💷 pound money
💳 credit card payment
🪙 coin money
💸 money flying spending expense
🧾 receipt bill invoice
🧮 abacus calculator budget
📊 chart statistics budget report
📈 chart increasing growth investment
📉 chart decreasing loss
🏷️ tag price label
💲 dollar sign money
💊 pill medicine pharmacy
💉 syringe vaccine injection
🩺 stethoscope doctor checkup
🏥 hospital clinic
🩹 bandage first aid
🦷 tooth dentist
🤒 sick ill fever
😴 sleep tired rest
🧘 yoga meditation wellness
🏋️ weightlifting gym fitness workout
🏃 running exercise jog
🏊 swimming pool
⚽ soccer football sports
🏀 basketball sports
🎾 tennis sports
🏸 badminton sports
🎱 billiards pool
🥊 boxing sports
⛳ golf
🏐 volleyball
🎬 movie film cinema
🎥 video camera film
🎮 video game gaming console
🕹️ joystick arcade game
🎲 dice board game
🎯 darts target
🧩 puzzle
🎵 music note song
🎧 headphones music
🎤 microphone karaoke singing
🎸 guitar music
🎹 piano keyboard music
🎨 art painting palette
📺 tv television streaming
📻 radio
🎟️ ticket event concert
🎭 theater drama performing arts
🎪 circus carnival
🎢 roller coaster theme park
🎡 ferris wheel amusement park
🎳 bowling
🎰 slot machine casino gambling
📷 camera photo
📸 camera flash photo
📚 books study school library
📖 book reading
📓 notebook
✏️ pencil school
📝 memo notes writing
🎓 graduation school tuition
🏫 school
💼 briefcase work business
🗂️ folder files
📎 paperclip
🖊️ pen
🧑‍💻 developer coder programmer work
🏖️ beach vacation
🏝️ island vacation
⛺ camping tent
🏕️ camping
🗺️ map travel
🧳 luggage travel suitcase
🏨 hotel stay
🛎️ bell hotel service
⛪ church
🕌 mosque
🛕 temple
🌍 earth world
🐶 dog pet
🐱 cat pet
🐟 fish pet
🐦 bird pet
🐾 paw pets
🦴 bone dog food
🌱 plant seedling garden
🌳 tree
🌻 sunflower flowers
💐 bouquet flowers
🪴 potted plant
☀️ sun weather
☔ umbrella rain weather
👶 baby infant
🍼 baby bottle milk formula
🧸 teddy bear toy kids
🎈 balloon party
🎉 party celebration
🎊 confetti party celebration
👨‍👩‍👧 family
❤️ heart love
💝 gift heart
💒 wedding
⭐ star favorite
✨ sparkles
🔔 bell notification reminder
📅 calendar date schedule
⏰ alarm clock time
🔒 lock security
🧰 toolbox tools
🪜 ladder
💎 gem diamond luxury
🏆 trophy prize
🎖️ medal
📌 pin
❓ question unknown other misc
➕ plus add
🙏 pray donation charity thanks
🤝 handshake deal
🧑‍🍳 cook chef
☑️ checklist done
""".trimIndent()

private class EmojiEntry(val emoji: String, val words: String)

private val ENTRIES: List<EmojiEntry> by lazy {
    EMOJI_DATA.lines().mapNotNull { line ->
        val l = line.trim()
        if (l.isEmpty()) null
        else EmojiEntry(l.substringBefore(' '), l.substringAfter(' ', "").lowercase())
    }
}

// Every word you type must appear in an emoji's keywords for it to match
fun searchEmojis(query: String): List<String> {
    val terms = query.lowercase().trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (terms.isEmpty()) return emptyList()
    return ENTRIES
        .filter { e -> terms.all { t -> e.words.contains(t) } }
        .map { it.emoji }
        .distinct()
        .take(150)
}