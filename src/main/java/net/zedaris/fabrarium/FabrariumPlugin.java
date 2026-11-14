package net.zedaris.fabrarium;

import java.time.Clock;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.zedaris.fabrarium.api.TechRpgService;
import net.zedaris.fabrarium.bukkit.FabrariumCommand;
import net.zedaris.fabrarium.bukkit.ConfigLoader;
import net.zedaris.fabrarium.bukkit.CustomItems;
import net.zedaris.fabrarium.bukkit.MachineListener;
import net.zedaris.fabrarium.bukkit.PluginSettings;
import net.zedaris.fabrarium.bukkit.RecipeCatalog;
import net.zedaris.fabrarium.bukkit.SchedulerBridge;
import net.zedaris.fabrarium.bukkit.TechRpgServiceImpl;
import net.zedaris.fabrarium.domain.MachineCoordinator;
import net.zedaris.fabrarium.persistence.JdbcTechRepository;
import net.zedaris.fabrarium.persistence.TechRepository;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

public final class FabrariumPlugin extends JavaPlugin {
    private ExecutorService bootstrapExecutor;
    private TechRepository repository;
    private RecipeCatalog recipes;
    private MachineCoordinator machines;
    private MachineListener machineListener;

    @Override
    public void onEnable() {
        bootstrapExecutor = Executors.newSingleThreadExecutor(Thread.ofPlatform()
                .name("fabrarium-bootstrap")
                .daemon(true)
                .factory());

        CompletableFuture.supplyAsync(() -> ConfigLoader.load(this), bootstrapExecutor)
                .thenCompose(this::initialize)
                .whenComplete((loadedMachines, failure) -> {
                    bootstrapExecutor.shutdown();
                    if (failure != null) {
                        getLogger().severe("Fabrarium nao iniciou: " + rootMessage(failure));
                        getServer().getGlobalRegionScheduler().execute(this,
                                () -> getServer().getPluginManager().disablePlugin(this));
                        return;
                    }
                    getServer().getGlobalRegionScheduler().execute(this, () -> activate(loadedMachines));
                });
    }

    private java.util.concurrent.CompletionStage<Integer> initialize(PluginSettings settings) {
        repository = new JdbcTechRepository(settings.database());
        machines = new MachineCoordinator(repository, Clock.systemUTC(), settings.processingTime());
        return repository.initialize().thenCompose(ignored -> machines.load());
    }

    private void activate(int loadedMachines) {
        if (!isEnabled()) return;
        CustomItems items = new CustomItems(this);
        recipes = new RecipeCatalog(this, items);
        recipes.register(getServer());

        SchedulerBridge scheduler = new SchedulerBridge(this);
        TechRpgService service = new TechRpgServiceImpl(repository, items, recipes);
        getServer().getServicesManager().register(TechRpgService.class, service, this, ServicePriority.Normal);

        machineListener = new MachineListener(this, machines, items, service, scheduler);
        getServer().getPluginManager().registerEvents(machineListener, this);

        FabrariumCommand commandHandler = new FabrariumCommand(service, scheduler);
        PluginCommand command = getCommand("fabrarium");
        if (command == null) throw new IllegalStateException("fabrarium command is missing from plugin.yml");
        command.setExecutor(commandHandler);
        command.setTabCompleter(commandHandler);

        getServer().getGlobalRegionScheduler().runAtFixedRate(this, task -> {
            for (var key : machines.keys()) {
                scheduler.region(key, location -> machineListener.tick(key, location));
            }
        }, 20L, 20L);

        getLogger().info("Fabrarium ativo; API " + TechRpgService.API_VERSION
                + ", " + loadedMachines + " maquina(s) restaurada(s). Contratos externos opcionais.");
    }

    @Override
    public void onDisable() {
        getServer().getServicesManager().unregisterAll(this);
        getServer().getGlobalRegionScheduler().cancelTasks(this);
        getServer().getAsyncScheduler().cancelTasks(this);
        if (recipes != null) recipes.unregister(getServer());
        if (repository != null) repository.close();
        if (bootstrapExecutor != null) bootstrapExecutor.shutdown();
    }

    private static String rootMessage(Throwable failure) {
        Throwable current = failure;
        while (current.getCause() != null) current = current.getCause();
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }
}
