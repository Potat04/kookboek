package nl.potat04.kookboek

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import nl.potat04.kookboek.data.Recipe
import nl.potat04.kookboek.ui.AddRecipeSheet
import nl.potat04.kookboek.ui.EditScreen
import nl.potat04.kookboek.ui.KookboekViewModel
import nl.potat04.kookboek.ui.LibraryScreen
import nl.potat04.kookboek.ui.ProvideImageStore
import nl.potat04.kookboek.ui.RecipeScreen
import nl.potat04.kookboek.ui.theme.KookboekTheme
import nl.potat04.kookboek.ui.theme.PaperBackground

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val openRecipe = intent?.getStringExtra(ShareActivity.EXTRA_OPEN_RECIPE)
        // Consume it: on a rotation the activity is rebuilt, and re-navigating would
        // yank the user back to this recipe after they had walked away from it.
        intent?.removeExtra(ShareActivity.EXTRA_OPEN_RECIPE)
        setContent {
            KookboekTheme {
                val vm: KookboekViewModel = viewModel(factory = KookboekViewModel.Factory)
                ProvideImageStore(vm.images) {
                    PaperBackground { Kookboek(vm, openRecipe) }
                }
            }
        }
    }
}

@Composable
private fun Kookboek(vm: KookboekViewModel, openRecipe: String?) {
    val nav = rememberNavController()
    val snackbars = remember { SnackbarHostState() }

    // Arrived here from the share sheet's "Openen" button.
    LaunchedEffect(openRecipe) {
        if (openRecipe != null) nav.navigate("recipe/$openRecipe")
    }

    LaunchedEffect(vm) {
        vm.messages.collect { toast ->
            val result = snackbars.showSnackbar(
                message = toast.message,
                actionLabel = toast.actionLabel,
                withDismissAction = toast.actionLabel == null,
            )
            if (result == SnackbarResult.ActionPerformed) toast.undo?.invoke()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbars) },
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.fillMaxSize(),
    ) { padding ->
        KookboekNavHost(nav, vm, padding)
    }
}

@Composable
private fun KookboekNavHost(
    nav: NavHostController,
    vm: KookboekViewModel,
    padding: PaddingValues,
) {
    val all by vm.all.collectAsStateWithLifecycle()
    val visible by vm.visible.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val favouritesOnly by vm.favouritesOnly.collectAsStateWithLifecycle()
    val sort by vm.sort.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val loaded by vm.loaded.collectAsStateWithLifecycle()

    // Recipes created by "write it yourself" only hit disk once you save them.
    var draft by remember { mutableStateOf<Recipe?>(null) }

    NavHost(navController = nav, startDestination = "library") {
        composable("library") {
            var addOpen by remember { mutableStateOf(false) }

            LibraryScreen(
                recipes = visible,
                total = all.size,
                query = query,
                favouritesOnly = favouritesOnly,
                sort = sort,
                onQuery = vm::setQuery,
                onToggleFavourites = vm::toggleFavouritesFilter,
                onSort = vm::setSort,
                onOpen = { nav.navigate("recipe/${it.id}") },
                onAdd = { addOpen = true },
                contentPadding = padding,
            )

            if (addOpen) {
                AddRecipeSheet(
                    busy = busy,
                    onDismiss = { addOpen = false },
                    onImport = { url ->
                        addOpen = false
                        vm.importUrl(url) { id -> id?.let { nav.navigate("recipe/$it") } }
                    },
                    onWriteOwn = {
                        addOpen = false
                        draft = Recipe(title = "")
                        nav.navigate("edit/new")
                    },
                )
            }
        }

        composable("recipe/{id}") { entry ->
            val id = entry.arguments?.getString("id")
            val recipe = all.firstOrNull { it.id == id }
            if (recipe == null) {
                // Gone from under us — leave rather than show a blank page.
                LeaveWhenGone(entry, nav, id, loaded)
            } else {
                RecipeScreen(
                    recipe = recipe,
                    busy = busy,
                    onBack = { nav.popBackStack() },
                    onEdit = { nav.navigate("edit/${recipe.id}") },
                    onToggleFavourite = { vm.toggleFavourite(recipe) },
                    onToggleIngredient = { vm.toggleIngredient(recipe, it) },
                    onToggleStep = { vm.toggleStep(recipe, it) },
                    onClearChecks = { vm.clearChecks(recipe) },
                    onNotes = { vm.setNotes(recipe, it) },
                    onRefresh = { vm.refresh(recipe) },
                    // Deleting only deletes. LeaveWhenGone above notices the recipe
                    // disappearing and walks back — one place decides, so we cannot
                    // pop twice and empty the whole back stack.
                    onDelete = { vm.delete(recipe) },
                    contentPadding = padding,
                )
            }
        }

        composable("edit/{id}") { entry ->
            val id = entry.arguments?.getString("id")
            val isNew = id == "new"
            val recipe = if (isNew) draft else all.firstOrNull { it.id == id }
            if (recipe == null) {
                LeaveWhenGone(entry, nav, id, loaded)
            } else {
                EditScreen(
                    original = recipe,
                    isNew = isNew,
                    onCancel = { nav.popBackStack() },
                    onSave = { edited ->
                        vm.save(edited)
                        draft = null
                        if (isNew) {
                            nav.popBackStack()
                            nav.navigate("recipe/${edited.id}")
                        } else {
                            nav.popBackStack()
                        }
                    },
                    contentPadding = padding,
                )
            }
        }
    }
}

/**
 * Walks back off a destination whose recipe no longer exists.
 *
 * Two things have to be true before giving up on a recipe:
 *  - the cookbook has actually been read from disk ([loaded]), otherwise a recipe
 *    opened straight from the share sheet gets abandoned before it ever appears;
 *  - this screen is still the one on top. A screen on its way out stays composed
 *    during the exit transition, and popping from there would take the library with
 *    it and leave an empty back stack — a blank window with a lone snackbar.
 */
@Composable
private fun LeaveWhenGone(
    entry: NavBackStackEntry,
    nav: NavHostController,
    key: Any?,
    loaded: Boolean,
) {
    LaunchedEffect(key, loaded) {
        if (!loaded) return@LaunchedEffect
        if (entry.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            nav.popBackStack()
        }
    }
}
