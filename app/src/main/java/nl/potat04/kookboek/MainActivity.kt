package nl.potat04.kookboek

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.activity.compose.BackHandler
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.launch
import nl.potat04.kookboek.data.SettingsStore
import nl.potat04.kookboek.ui.AddRecipeSheet
import nl.potat04.kookboek.ui.CookScreen
import nl.potat04.kookboek.ui.DeletedScreen
import nl.potat04.kookboek.ui.EditScreen
import nl.potat04.kookboek.ui.KookboekViewModel
import nl.potat04.kookboek.ui.LibraryScreen
import nl.potat04.kookboek.ui.LocalImageStore
import nl.potat04.kookboek.ui.ChallengeOverlay
import nl.potat04.kookboek.ui.ProvideImageStore
import nl.potat04.kookboek.ui.RecipeScreen
import nl.potat04.kookboek.ui.SettingsScreen
import nl.potat04.kookboek.ui.UpdateHost
import nl.potat04.kookboek.ui.UpdateSettings
import nl.potat04.kookboek.ui.resolve
import nl.potat04.kookboek.ui.shareRecipeFile
import nl.potat04.kookboek.ui.shareRecipeText
import nl.potat04.kookboek.ui.setAppLanguage
import nl.potat04.kookboek.ui.theme.KookboekTheme
import nl.potat04.kookboek.ui.theme.PaperBackground
import nl.potat04.kookboek.ui.theme.paintWindowFor

class MainActivity : ComponentActivity() {

    /** What a launcher shortcut asked for, until the navigation has acted on it. */
    private var shortcut by mutableStateOf<Shortcut?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val openRecipe = intent?.getStringExtra(ShareActivity.EXTRA_OPEN_RECIPE)
        // Consume it: on a rotation the activity is rebuilt, and re-navigating would
        // yank the user back to this recipe after they had walked away from it.
        intent?.removeExtra(ShareActivity.EXTRA_OPEN_RECIPE)
        shortcut = Shortcuts.consume(intent)
        val store = (application as KookboekApp).settings
        paintWindowFor(store.settings.value)
        setContent {
            val settings by store.settings.collectAsStateWithLifecycle()
            KookboekTheme(settings = settings) {
                val vm: KookboekViewModel = viewModel(factory = KookboekViewModel.Factory)
                ProvideImageStore(vm.images) {
                    PaperBackground {
                        Kookboek(vm, store, openRecipe, shortcut) { shortcut = null }
                    }
                    // Refreshing a recipe can run into a bot check just as importing can.
                    ChallengeOverlay()
                }
            }
        }
    }

    /**
     * A shortcut tapped while the app is already running lands here and not in
     * [onCreate], because this activity is `singleTop` and the launcher brings the
     * existing task forward rather than starting a second one.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        Shortcuts.consume(intent)?.let { shortcut = it }
    }
}

@Composable
private fun Kookboek(
    vm: KookboekViewModel,
    store: SettingsStore,
    openRecipe: String?,
    shortcut: Shortcut?,
    onShortcutDone: () -> Unit,
) {
    val nav = rememberNavController()
    val snackbars = remember { SnackbarHostState() }
    val context = LocalContext.current

    // Arrived here from the share sheet's "Openen" button.
    LaunchedEffect(openRecipe) {
        if (openRecipe != null) nav.navigate("recipe/$openRecipe")
    }

    LaunchedEffect(vm, context) {
        vm.messages.collect { toast ->
            val result = snackbars.showSnackbar(
                message = toast.message.resolve(context),
                actionLabel = toast.actionLabel?.resolve(context),
                withDismissAction = toast.actionLabel == null,
                // Compose keeps a snackbar with an action up for ever unless told
                // otherwise. Ten seconds is enough to reach for Undo, and after that
                // the message must not sit over a library that has moved on.
                duration = if (toast.actionLabel != null) SnackbarDuration.Long else SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) toast.undo?.invoke()
        }
    }

    val updates = (context.applicationContext as KookboekApp).updates
    UpdateHost(updates) { updateState, openUpdates ->
        Scaffold(
            snackbarHost = { SnackbarHost(snackbars) },
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onBackground,
            // Keep scrolling content and controls outside the system navigation buttons.
            // This consumes the inset so Scaffold does not add the same spacing again.
            modifier = Modifier.fillMaxSize().navigationBarsPadding(),
        ) { padding ->
            KookboekNavHost(nav, vm, store, padding, shortcut, onShortcutDone) {
                if (updates.enabled) UpdateSettings(updateState, openUpdates)
            }
        }
    }
}

@Composable
private fun KookboekNavHost(
    nav: NavHostController,
    vm: KookboekViewModel,
    store: SettingsStore,
    padding: PaddingValues,
    shortcut: Shortcut?,
    onShortcutDone: () -> Unit,
    updateContent: @Composable () -> Unit,
) {
    val all by vm.all.collectAsStateWithLifecycle()
    val visible by vm.visible.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val favouritesOnly by vm.favouritesOnly.collectAsStateWithLifecycle()
    val sort by vm.sort.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val selection by vm.selection.collectAsStateWithLifecycle()
    val loaded by vm.loaded.collectAsStateWithLifecycle()
    val settings by store.settings.collectAsStateWithLifecycle()

    // Recipes created by "write it yourself" only hit disk once you save them. The
    // ViewModel keeps them, so turning the phone mid-sentence does not lose the page.
    val draft by vm.draft.collectAsStateWithLifecycle()
    // Up here rather than inside the library route, because the "Paste a link" shortcut
    // opens it from outside the navigation.
    var addOpen by remember { mutableStateOf(false) }

    // Both shortcuts land on the library: one turns the filter on, the other opens the
    // sheet. Neither navigates anywhere new, so a reader who was deep in a recipe when
    // they tapped it comes back to the shelf rather than to a stack they cannot see.
    LaunchedEffect(shortcut) {
        when (shortcut) {
            null -> return@LaunchedEffect
            Shortcut.FAVOURITES -> {
                vm.setFavouritesFilter(true)
                nav.popBackStack("library", inclusive = false)
            }
            Shortcut.ADD_LINK -> {
                nav.popBackStack("library", inclusive = false)
                addOpen = true
            }
        }
        onShortcutDone()
    }

    NavHost(navController = nav, startDestination = "library") {
        composable("library") {
            val context = LocalContext.current
            val scope = rememberCoroutineScope()
            val images = LocalImageStore.current
            val focus = LocalFocusManager.current
            // Kept on the route and not inside the screen, so that walking into a recipe
            // and back lands on the same card. navigation-compose keeps a destination's
            // saveable state while it is on the back stack; this is that state, named.
            val listState = rememberLazyListState()

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
                onSettings = { nav.navigate("settings") },
                selection = selection,
                busy = busy,
                // Two recipes is the first moment picking several is worth knowing about.
                showHoldHint = !settings.holdHintSeen && all.size >= 2,
                onToggleSelected = { vm.toggleSelected(it.id) },
                onClearSelection = vm::clearSelection,
                onDeleteSelected = vm::deleteSelected,
                onRefreshSelected = vm::refreshSelected,
                // Read at the tap, not held: the selection is ids and the list moves.
                onShareSelected = { context.shareRecipeText(vm.selectedRecipes()) },
                onSendSelected = {
                    val chosen = vm.selectedRecipes()
                    // Writing the zip is real work, so it waits for a scope rather than
                    // holding up the menu closing.
                    images?.let { pictures -> scope.launch { context.shareRecipeFile(chosen, pictures) } }
                },
                listState = listState,
                contentPadding = padding,
            )

            // Back drops the selection before it leaves the library, the way every
            // other app that has a selection mode behaves.
            BackHandler(enabled = selection.isNotEmpty()) { vm.clearSelection() }

            // And with something typed, Back empties the search box first. Back out of
            // a search is what the gesture means everywhere else, and closing the whole
            // app instead is a long way from what was asked.
            BackHandler(enabled = selection.isEmpty() && query.isNotEmpty()) {
                vm.setQuery("")
                focus.clearFocus()
            }

            if (addOpen) {
                AddRecipeSheet(
                    busy = busy,
                    // Swiping the sheet away while a page is being fetched means the
                    // same as the Cancel button under it.
                    onDismiss = {
                        vm.cancelImport()
                        addOpen = false
                    },
                    // The sheet stays up while the page is being fetched, because that
                    // is where the Cancel button lives.
                    onImport = { url ->
                        vm.importUrl(url) { id ->
                            // A sheet dismissed while the page was still coming in said
                            // "not now": the recipe is kept and the snackbar says so, but
                            // nobody gets dragged onto a screen they did not ask for.
                            val wanted = addOpen
                            addOpen = false
                            if (wanted) id?.let { nav.navigate("recipe/$it") }
                        }
                    },
                    onCancel = {
                        vm.cancelImport()
                        addOpen = false
                    },
                    onWriteOwn = {
                        addOpen = false
                        vm.startDraft()
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
                    onCook = { nav.navigate("cook/${recipe.id}") },
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
                    // A tag or a site name becomes the library's search.
                    onSearch = { text ->
                        vm.setQuery(text)
                        nav.popBackStack("library", inclusive = false)
                    },
                    onServings = { vm.setCookedServings(recipe, it) },
                    onMadeIt = { vm.markCooked(recipe, it) },
                    onNotify = vm::notify,
                    onOpened = { vm.markOpened(recipe) },
                    haptics = settings.hapticFeedback,
                    contentPadding = padding,
                )
            }
        }

        composable("cook/{id}") { entry ->
            val id = entry.arguments?.getString("id")
            val recipe = all.firstOrNull { it.id == id }
            if (recipe == null) {
                LeaveWhenGone(entry, nav, id, loaded)
            } else {
                CookScreen(
                    recipe = recipe,
                    // Back and the close control do the same thing: land on the recipe
                    // screen, at the recipe you were cooking.
                    onClose = { nav.popBackStack() },
                    onToggleStep = { vm.toggleStep(recipe, it) },
                    onToggleIngredient = { vm.toggleIngredient(recipe, it) },
                    onNotify = vm::notify,
                    hintSeen = settings.cookHintSeen,
                    onHintSeen = { store.setCookHintSeen(true) },
                    contentPadding = padding,
                )
            }
        }

        composable("settings") {
            val settings by store.settings.collectAsStateWithLifecycle()
            val context = LocalContext.current
            SettingsScreen(
                settings = settings,
                onPalette = store::setPalette,
                onMode = store::setMode,
                onTextSize = store::setTextSize,
                // Handing the language to the platform restarts this activity; the
                // navigation back stack is restored, so we come back here.
                onLanguage = context::setAppLanguage,
                onHaptics = store::setHapticFeedback,
                onAutoBackup = store::setAutoBackup,
                // KookboekApp watches this and re-schedules the daily job.
                onBackupFolder = store::setBackupFolder,
                onExport = { vm.exportTo(context.contentResolver, it) },
                onRestore = { vm.restoreFrom(context.contentResolver, it) },
                onDeleted = { nav.navigate("deleted") },
                updateContent = updateContent,
                onBack = { nav.popBackStack() },
                contentPadding = padding,
            )
        }

        composable("deleted") {
            val binned by vm.deleted.collectAsStateWithLifecycle()
            DeletedScreen(
                recipes = binned,
                onRestore = vm::restoreDeleted,
                onDeleteForever = vm::deleteForever,
                onBack = { nav.popBackStack() },
                contentPadding = padding,
            )
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
                        vm.clearDraft()
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
