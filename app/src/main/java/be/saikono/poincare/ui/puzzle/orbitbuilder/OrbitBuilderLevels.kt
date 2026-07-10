package be.saikono.poincare.ui.puzzle.orbitbuilder

object OrbitBuilderLevels {
    val all: List<OrbitBuilderLevel> = listOf(
        OrbitBuilderLevel(
            id = "orbit-easy-01-add-10",
            grade = 1,
            difficultyStars = 1,
            title = "Pluspoort",
            instruction = "Vlieg door de juiste poort. Er staan extra getallen op de kaart, maar je hebt maar één poort nodig.",
            startRow = 7,
            startColumn = 0,
            initialDirection = OrbitDirection.UP,
            targetRow = 0,
            targetColumn = 5,
            startValue = 10,
            targetValue = 20,
            maxPlaceableArrows = 18,
            optimalArrowCount = 10,
            fixedTiles = tiles(
                gate(5, 1, OrbitGateOperation.ADD, 10),
                gate(6, 3, OrbitGateOperation.ADD, 5),
                gate(4, 4, OrbitGateOperation.ADD, 20),
                gate(2, 2, OrbitGateOperation.SUBTRACT, 5),
                gate(1, 4, OrbitGateOperation.ADD, 30),
                asteroid(6, 2), asteroid(4, 1), asteroid(3, 3), asteroid(1, 1)
            )
        ),
        OrbitBuilderLevel(
            id = "orbit-easy-02-add-25",
            grade = 1,
            difficultyStars = 1,
            title = "Ronde getallen",
            instruction = "Zoek de poort die van de startwaarde meteen de doelwaarde maakt.",
            startRow = 7,
            startColumn = 1,
            initialDirection = OrbitDirection.UP,
            targetRow = 0,
            targetColumn = 4,
            startValue = 25,
            targetValue = 50,
            maxPlaceableArrows = 18,
            optimalArrowCount = 9,
            fixedTiles = tiles(
                gate(4, 2, OrbitGateOperation.ADD, 25),
                gate(6, 0, OrbitGateOperation.ADD, 10),
                gate(5, 4, OrbitGateOperation.SUBTRACT, 5),
                gate(3, 5, OrbitGateOperation.ADD, 50),
                gate(1, 2, OrbitGateOperation.ADD, 15),
                asteroid(6, 2), asteroid(5, 0), asteroid(3, 1), asteroid(2, 4), asteroid(1, 5)
            )
        ),
        OrbitBuilderLevel(
            id = "orbit-easy-03-add-60",
            grade = 2,
            difficultyStars = 1,
            title = "Grote sprong",
            instruction = "Er staan meer getallen, maar één eenvoudige pluspoort is genoeg.",
            startRow = 7,
            startColumn = 0,
            initialDirection = OrbitDirection.UP,
            targetRow = 0,
            targetColumn = 5,
            startValue = 40,
            targetValue = 100,
            maxPlaceableArrows = 20,
            optimalArrowCount = 11,
            fixedTiles = tiles(
                gate(3, 2, OrbitGateOperation.ADD, 60),
                gate(6, 4, OrbitGateOperation.ADD, 20),
                gate(5, 1, OrbitGateOperation.ADD, 40),
                gate(4, 5, OrbitGateOperation.SUBTRACT, 10),
                gate(1, 3, OrbitGateOperation.ADD, 30),
                asteroid(6, 1), asteroid(5, 3), asteroid(4, 1), asteroid(2, 4), asteroid(1, 0)
            )
        ),
        OrbitBuilderLevel(
            id = "orbit-easy-04-sub-10",
            grade = 2,
            difficultyStars = 1,
            title = "Minderpoort",
            instruction = "Vlieg door de juiste minpoort en ontwijk de asteroïden.",
            startRow = 7,
            startColumn = 2,
            initialDirection = OrbitDirection.UP,
            targetRow = 0,
            targetColumn = 3,
            startValue = 70,
            targetValue = 60,
            maxPlaceableArrows = 20,
            optimalArrowCount = 8,
            fixedTiles = tiles(
                gate(4, 2, OrbitGateOperation.SUBTRACT, 10),
                gate(6, 5, OrbitGateOperation.ADD, 10),
                gate(5, 0, OrbitGateOperation.SUBTRACT, 20),
                gate(3, 4, OrbitGateOperation.ADD, 30),
                gate(1, 1, OrbitGateOperation.SUBTRACT, 5),
                asteroid(6, 1), asteroid(5, 3), asteroid(3, 1), asteroid(2, 5), asteroid(1, 4)
            )
        ),
        OrbitBuilderLevel(
            id = "orbit-medium-01-times-3",
            grade = 3,
            difficultyStars = 2,
            title = "Keer drie",
            instruction = "Er zijn veel poorten, maar je hoeft maar één vermenigvuldiging te kiezen.",
            startRow = 7,
            startColumn = 0,
            initialDirection = OrbitDirection.UP,
            targetRow = 0,
            targetColumn = 5,
            startValue = 12,
            targetValue = 36,
            maxPlaceableArrows = 22,
            optimalArrowCount = 11,
            fixedTiles = tiles(
                gate(4, 2, OrbitGateOperation.MULTIPLY, 3),
                gate(6, 2, OrbitGateOperation.ADD, 12),
                gate(5, 5, OrbitGateOperation.MULTIPLY, 2),
                gate(3, 0, OrbitGateOperation.ADD, 24),
                gate(2, 3, OrbitGateOperation.DIVIDE, 2),
                gate(1, 4, OrbitGateOperation.SUBTRACT, 6),
                asteroid(6, 1), asteroid(5, 3), asteroid(4, 5), asteroid(2, 1), asteroid(1, 2)
            )
        ),
        OrbitBuilderLevel(
            id = "orbit-medium-02-half-80",
            grade = 3,
            difficultyStars = 2,
            title = "Halveren",
            instruction = "Kies de deelpoort die het getal in één stap juist maakt.",
            startRow = 7,
            startColumn = 1,
            initialDirection = OrbitDirection.UP,
            targetRow = 0,
            targetColumn = 4,
            startValue = 80,
            targetValue = 40,
            maxPlaceableArrows = 22,
            optimalArrowCount = 10,
            fixedTiles = tiles(
                gate(4, 3, OrbitGateOperation.DIVIDE, 2),
                gate(6, 0, OrbitGateOperation.SUBTRACT, 20),
                gate(5, 5, OrbitGateOperation.ADD, 40),
                gate(3, 1, OrbitGateOperation.DIVIDE, 4),
                gate(2, 5, OrbitGateOperation.MULTIPLY, 2),
                gate(1, 2, OrbitGateOperation.ADD, 10),
                asteroid(6, 2), asteroid(5, 1), asteroid(4, 5), asteroid(3, 4), asteroid(1, 0)
            )
        ),
        OrbitBuilderLevel(
            id = "orbit-medium-03-times-3-30",
            grade = 4,
            difficultyStars = 2,
            title = "Dertig naar negentig",
            instruction = "Gebruik weinig stappen in je berekening. De rest zijn afleiders.",
            startRow = 7,
            startColumn = 0,
            initialDirection = OrbitDirection.UP,
            targetRow = 0,
            targetColumn = 5,
            startValue = 30,
            targetValue = 90,
            maxPlaceableArrows = 24,
            optimalArrowCount = 11,
            fixedTiles = tiles(
                gate(5, 2, OrbitGateOperation.MULTIPLY, 3),
                gate(6, 4, OrbitGateOperation.ADD, 30),
                gate(4, 0, OrbitGateOperation.MULTIPLY, 2),
                gate(3, 4, OrbitGateOperation.SUBTRACT, 15),
                gate(2, 2, OrbitGateOperation.DIVIDE, 3),
                gate(1, 1, OrbitGateOperation.ADD, 60),
                asteroid(6, 1), asteroid(5, 4), asteroid(4, 3), asteroid(2, 0), asteroid(1, 3), asteroid(0, 1)
            )
        ),
        OrbitBuilderLevel(
            id = "orbit-medium-04-add-30",
            grade = 4,
            difficultyStars = 2,
            title = "Vijfenveertig plus dertig",
            instruction = "Veel getallen op de kaart, maar de juiste berekening blijft kort.",
            startRow = 7,
            startColumn = 2,
            initialDirection = OrbitDirection.UP,
            targetRow = 0,
            targetColumn = 3,
            startValue = 45,
            targetValue = 75,
            maxPlaceableArrows = 24,
            optimalArrowCount = 9,
            fixedTiles = tiles(
                gate(4, 2, OrbitGateOperation.ADD, 30),
                gate(6, 0, OrbitGateOperation.ADD, 15),
                gate(5, 4, OrbitGateOperation.MULTIPLY, 2),
                gate(3, 5, OrbitGateOperation.SUBTRACT, 10),
                gate(2, 1, OrbitGateOperation.ADD, 45),
                gate(1, 4, OrbitGateOperation.DIVIDE, 3),
                asteroid(6, 3), asteroid(5, 1), asteroid(4, 4), asteroid(3, 0), asteroid(1, 2)
            )
        ),
        OrbitBuilderLevel(
            id = "orbit-hard-01-times-3-100",
            grade = 5,
            difficultyStars = 3,
            title = "Honderd naar driehonderd",
            instruction = "Kies de eenvoudige vermenigvuldiging en laat de moeilijke afleiders liggen.",
            startRow = 7,
            startColumn = 0,
            initialDirection = OrbitDirection.UP,
            targetRow = 0,
            targetColumn = 5,
            startValue = 100,
            targetValue = 300,
            maxPlaceableArrows = 26,
            optimalArrowCount = 11,
            fixedTiles = tiles(
                gate(4, 2, OrbitGateOperation.MULTIPLY, 3),
                gate(6, 3, OrbitGateOperation.ADD, 100),
                gate(5, 5, OrbitGateOperation.MULTIPLY, 2),
                gate(3, 0, OrbitGateOperation.DIVIDE, 2),
                gate(2, 3, OrbitGateOperation.SUBTRACT, 50),
                gate(1, 4, OrbitGateOperation.ADD, 200),
                asteroid(6, 1), asteroid(5, 3), asteroid(4, 5), asteroid(3, 4), asteroid(2, 1), asteroid(1, 2)
            )
        ),
        OrbitBuilderLevel(
            id = "orbit-hard-02-half-150",
            grade = 5,
            difficultyStars = 3,
            title = "De helft",
            instruction = "De kaart is drukker, maar je berekening is kort: zoek de helft.",
            startRow = 7,
            startColumn = 1,
            initialDirection = OrbitDirection.UP,
            targetRow = 0,
            targetColumn = 4,
            startValue = 150,
            targetValue = 75,
            maxPlaceableArrows = 26,
            optimalArrowCount = 10,
            fixedTiles = tiles(
                gate(4, 3, OrbitGateOperation.DIVIDE, 2),
                gate(6, 5, OrbitGateOperation.ADD, 50),
                gate(5, 0, OrbitGateOperation.SUBTRACT, 75),
                gate(3, 1, OrbitGateOperation.MULTIPLY, 2),
                gate(2, 5, OrbitGateOperation.DIVIDE, 3),
                gate(1, 2, OrbitGateOperation.ADD, 25),
                asteroid(6, 2), asteroid(5, 4), asteroid(4, 0), asteroid(3, 5), asteroid(2, 2), asteroid(1, 0)
            )
        ),
        OrbitBuilderLevel(
            id = "orbit-hard-03-times-2-250",
            grade = 6,
            difficultyStars = 3,
            title = "Verdubbelen",
            instruction = "Meer obstakels en meer poorten, maar één poort geeft de juiste waarde.",
            startRow = 7,
            startColumn = 0,
            initialDirection = OrbitDirection.UP,
            targetRow = 0,
            targetColumn = 5,
            startValue = 250,
            targetValue = 500,
            maxPlaceableArrows = 28,
            optimalArrowCount = 11,
            fixedTiles = tiles(
                gate(5, 2, OrbitGateOperation.MULTIPLY, 2),
                gate(6, 4, OrbitGateOperation.ADD, 250),
                gate(4, 0, OrbitGateOperation.DIVIDE, 5),
                gate(3, 3, OrbitGateOperation.ADD, 100),
                gate(2, 5, OrbitGateOperation.SUBTRACT, 50),
                gate(1, 2, OrbitGateOperation.MULTIPLY, 3),
                asteroid(6, 1), asteroid(5, 4), asteroid(4, 4), asteroid(3, 1), asteroid(2, 3), asteroid(1, 0), asteroid(0, 2)
            )
        ),
        OrbitBuilderLevel(
            id = "orbit-hard-04-add-80",
            grade = 6,
            difficultyStars = 3,
            title = "Tachtig erbij",
            instruction = "Laat je niet misleiden door alle cijfers. De juiste route heeft een eenvoudige berekening.",
            startRow = 7,
            startColumn = 2,
            initialDirection = OrbitDirection.UP,
            targetRow = 0,
            targetColumn = 3,
            startValue = 120,
            targetValue = 200,
            maxPlaceableArrows = 28,
            optimalArrowCount = 9,
            fixedTiles = tiles(
                gate(4, 2, OrbitGateOperation.ADD, 80),
                gate(6, 0, OrbitGateOperation.MULTIPLY, 2),
                gate(5, 5, OrbitGateOperation.ADD, 120),
                gate(3, 4, OrbitGateOperation.DIVIDE, 2),
                gate(2, 1, OrbitGateOperation.SUBTRACT, 20),
                gate(1, 5, OrbitGateOperation.ADD, 40),
                asteroid(6, 3), asteroid(5, 1), asteroid(4, 4), asteroid(3, 0), asteroid(2, 5), asteroid(1, 2)
            )
        )
    )

    private fun tiles(vararg entries: Pair<OrbitPosition, OrbitFixedTile>): Map<OrbitPosition, OrbitFixedTile> = mapOf(*entries)

    private fun gate(
        row: Int,
        column: Int,
        operation: OrbitGateOperation,
        amount: Int
    ): Pair<OrbitPosition, OrbitFixedTile> = OrbitPosition(row, column) to OrbitFixedTile.Gate(OrbitMathGate(operation, amount))

    private fun asteroid(row: Int, column: Int): Pair<OrbitPosition, OrbitFixedTile> =
        OrbitPosition(row, column) to OrbitFixedTile.Asteroid
}
