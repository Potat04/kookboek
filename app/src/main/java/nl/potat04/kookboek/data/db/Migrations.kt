package nl.potat04.kookboek.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Every schema step the app has ever taken, oldest first. Room runs the ones between
 * the version on disk and the version in [KookboekDatabase].
 *
 * The SQL is copied from the `createSql` in `app/schemas/.../<version>.json`, not
 * written from memory: Room checks the migrated tables against what it expects and
 * refuses to open the database on any difference, however small.
 */
object Migrations {

    /**
     * Version 2: the reader's own bookkeeping on a recipe (servings cooked for, last
     * cooked, last edited, first opened, soft delete, attached photo), the page's
     * split times and video, ingredient group headings, and labels.
     */
    val FROM_1_TO_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            listOf(
                "cookedServings INTEGER",
                "lastCookedAt INTEGER",
                "editedAt INTEGER",
                "prepMinutes INTEGER",
                "cookMinutes INTEGER",
                "videoUrl TEXT",
                "deletedAt INTEGER",
                "attachmentFile TEXT",
                "openedAt INTEGER",
            ).forEach { db.execSQL("ALTER TABLE `recipes` ADD COLUMN $it") }

            db.execSQL("ALTER TABLE `ingredients` ADD COLUMN `section` TEXT")

            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `labels` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, " +
                    "`position` INTEGER NOT NULL, PRIMARY KEY(`id`))"
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `recipe_labels` (`recipeId` TEXT NOT NULL, " +
                    "`labelId` TEXT NOT NULL, PRIMARY KEY(`recipeId`, `labelId`), " +
                    "FOREIGN KEY(`recipeId`) REFERENCES `recipes`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , " +
                    "FOREIGN KEY(`labelId`) REFERENCES `labels`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_recipe_labels_recipeId` ON `recipe_labels` (`recipeId`)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_recipe_labels_labelId` ON `recipe_labels` (`labelId`)"
            )
        }
    }

    val ALL: Array<Migration> = arrayOf(FROM_1_TO_2)
}
