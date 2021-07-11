package com.lrozanski.dbgen

import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.random.Random
import kotlin.reflect.KClass

enum class PetType {
    DOG,
    CAT
}

object Pets : LongIdTable() {
    val name = varchar("name", 255)
    val type = enumerationByName("type", 25, PetType::class)
}

class Generator {
    val entries: MutableList<TableEntry> = mutableListOf()
}

class RowGenerator<T>(val column: Column<T>, val generator: () -> Any)

fun stringGenerator(column: Column<String>, lengthRange: IntRange) = RowGenerator(column) {
    val actualLength = lengthRange.random()
    val allowedChars = ('A'..'Z') + ('a'..'z') + ('0'..'9') + '_'

    (1..actualLength)
        .map { allowedChars.random() }
        .joinToString("")
}

inline fun <reified T : Enum<T>> enumGenerator(column: Column<T>, enumType: KClass<T>) = RowGenerator(column) {
    val enumConstants = enumType.java.enumConstants
    val index = Random.nextInt(enumConstants.size)

    return@RowGenerator enumConstants[index]
}

class TableEntry(val table: Table) {
    var rows = 50
    lateinit var generators: List<RowGenerator<in Any>>
}

fun generate(init: Generator.() -> Unit) {
    val generator = Generator().apply(init)

    generator.entries.forEach { tableEntry ->
        transaction {
            (0 until tableEntry.rows).forEach { _ ->
                tableEntry.table.insert { insert ->
                    tableEntry.generators.forEach { generator ->
                        insert[generator.column] = generator.generator()
                    }
                }
            }
        }
    }
}

fun Generator.table(table: Table, init: TableEntry.() -> Unit) {
    val tableEntry = TableEntry(table)
    init(tableEntry)
    entries.add(tableEntry)
}

@Suppress("UNCHECKED_CAST")
fun TableEntry.generators(vararg generator: RowGenerator<*>) {
    generators = generator.map { it as RowGenerator<in Any> }
}

fun main() {
    Database.connect("jdbc:postgresql://localhost:5432/postgres", user = "postgres", password = "postgres")

    transaction {
        addLogger(StdOutSqlLogger)
        SchemaUtils.create(Pets)
    }

    println(stringGenerator(Pets.name, 8..16).generator())
    println(enumGenerator(Pets.type, PetType::class).generator())

    generate {
        table(Pets) {
            rows = 25
            generators(
                stringGenerator(Pets.name, 16..64),
                enumGenerator(Pets.type, PetType::class)
            )
        }
    }
}
