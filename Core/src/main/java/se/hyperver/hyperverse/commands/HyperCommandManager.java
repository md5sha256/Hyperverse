//
// Hyperverse - A Minecraft world management plugin
//
// This program is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program. If not, see <http://www.gnu.org/licenses/>.
//

package se.hyperver.hyperverse.commands;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.BukkitCommandManager;
import co.aikar.commands.CommandHelp;
import co.aikar.commands.annotation.Optional;
import co.aikar.commands.annotation.*;
import co.aikar.taskchain.TaskChainFactory;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.inject.Inject;
import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import se.hyperver.hyperverse.Hyperverse;
import se.hyperver.hyperverse.configuration.FileHyperConfiguration;
import se.hyperver.hyperverse.configuration.Message;
import se.hyperver.hyperverse.configuration.Messages;
import se.hyperver.hyperverse.exception.HyperWorldValidationException;
import se.hyperver.hyperverse.flags.FlagParseException;
import se.hyperver.hyperverse.flags.GlobalWorldFlagContainer;
import se.hyperver.hyperverse.flags.WorldFlag;
import se.hyperver.hyperverse.flags.implementation.PlayerLimitFlag;
import se.hyperver.hyperverse.modules.HyperWorldFactory;
import se.hyperver.hyperverse.util.IncendoPaster;
import se.hyperver.hyperverse.util.MessageUtil;
import se.hyperver.hyperverse.util.SeedUtil;
import se.hyperver.hyperverse.util.WorldUtil;
import se.hyperver.hyperverse.world.*;

import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.nio.file.Files;
import java.text.DecimalFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@CommandAlias("hyperverse|hv|worlds|world")
@CommandPermission("hyperverse.worlds")
@SuppressWarnings("unused")
public class HyperCommandManager extends BaseCommand {

    private final WorldManager worldManager;
    private final FileHyperConfiguration fileHyperConfiguration;
    private final HyperWorldFactory hyperWorldFactory;
    private final GlobalWorldFlagContainer globalFlagContainer;
    private final TaskChainFactory taskChainFactory;

    @Inject public HyperCommandManager(final Hyperverse hyperverse, final WorldManager worldManager,
        final HyperWorldFactory hyperWorldFactory, final GlobalWorldFlagContainer globalFlagContainer,
        final TaskChainFactory taskChainFactory, final FileHyperConfiguration hyperConfiguration) {
        this.worldManager = Objects.requireNonNull(worldManager);
        this.hyperWorldFactory = Objects.requireNonNull(hyperWorldFactory);
        this.globalFlagContainer = Objects.requireNonNull(globalFlagContainer);
        this.taskChainFactory = Objects.requireNonNull(taskChainFactory);
        this.fileHyperConfiguration = Objects.requireNonNull(hyperConfiguration);
        // Create the command manager
        final BukkitCommandManager bukkitCommandManager = new BukkitCommandManager(hyperverse);
        bukkitCommandManager.getCommandCompletions().registerAsyncCompletion("hyperworlds",
            context -> worldManager.getWorlds().stream().filter(hyperWorld -> {
                final String stateSel = context.getConfig("state", "").toLowerCase();
                final String playerSel = context.getConfig("players", "").toLowerCase();
                if (!hyperWorld.isLoaded()) {
                    return false;
                }
                assert hyperWorld.getBukkitWorld() != null;
                boolean ret = true;
                switch (stateSel) {
                    case "loaded":
                        ret = hyperWorld.isLoaded(); break;
                    case "not_loaded":
                        ret = !hyperWorld.isLoaded(); break;
                    default:
                        break;
                }
                switch (playerSel) {
                    case "no_players":
                        ret = ret && hyperWorld.getBukkitWorld().getPlayers().isEmpty(); break;
                    case "has_players":
                        ret = ret && !hyperWorld.getBukkitWorld().getPlayers().isEmpty(); break;
                    default:
                        break;
                }
                return ret;

            }).map(HyperWorld::getConfiguration).map(WorldConfiguration::getName)
                .filter(worldName -> {
                    final String selection = context.getConfig("player", "").toLowerCase();
                    final boolean inWorld =
                        worldName.equalsIgnoreCase(context.getPlayer().getWorld().getName());
                    switch (selection) {
                        case "not_in":
                            return !inWorld;
                        case "in":
                            return inWorld;
                        default:
                            return true;
                    }
                }).collect(Collectors.toList()));
        bukkitCommandManager.getCommandCompletions()
            .registerAsyncCompletion("import-candidates", context -> {
                final File baseDirectory = Bukkit.getWorldContainer();
                try {
                    return Files.list(baseDirectory.toPath()).filter(path -> {
                        final File file = path.toFile();
                        return file.isDirectory() && new File(file, "level.dat").isFile()
                            && this.worldManager.getWorld(file.getName()) == null;
                    }).map(path -> path.toFile().getName()).sorted(Comparator.naturalOrder())
                        .collect(Collectors.toList());
                } catch (IOException ex) {
                    return Collections.emptyList();
                }
            });
        bukkitCommandManager.getCommandCompletions().registerCompletion("worldtypes", context -> {
            if (context.getInput().contains(" ")) {
                return Collections.emptyList();
            }
            return Arrays.stream(WorldType.values()).map(WorldType::name).map(String::toLowerCase)
                .collect(Collectors.toList());
        });
        bukkitCommandManager.getCommandCompletions().registerCompletion("null", context ->
            Collections.emptyList());
        bukkitCommandManager.getCommandCompletions()
            .registerAsyncCompletion("generators", context -> {
                final String arg = context.getInput();
                if (arg.contains(":")) {
                    return Collections.emptyList();
                }
                final List<String> generators = new ArrayList<>();
                generators.add("vanilla");
                for (final Plugin plugin : Bukkit.getPluginManager().getPlugins()) {
                    generators.add(plugin.getName().toLowerCase());
                }
                return generators;
            });
        bukkitCommandManager.getCommandCompletions().registerCompletion("flags", context ->
            globalFlagContainer.getFlagMap().values().stream().map(WorldFlag::getName).collect(
                Collectors.toList()));
        bukkitCommandManager.getCommandCompletions().registerCompletion("gamerules", context ->
            Arrays.stream(GameRule.values()).map(GameRule::getName).collect(Collectors.toList()));
        bukkitCommandManager.getCommandCompletions().registerCompletion("flag", context -> {
            final WorldFlag<?, ?> flag = context.getContextValue(WorldFlag.class);
            if (flag != null) {
                return flag.getTabCompletions();
            }
            return Collections.emptyList();
        });
        bukkitCommandManager.getCommandCompletions().registerCompletion("gamerule", context -> {
            final GameRule<?> gameRule = context.getContextValue(GameRule.class);
            if (gameRule != null) {
                if (gameRule.getType() == Boolean.class) {
                    return Arrays.asList("true", "false");
                }
            }
            return Collections.emptyList();
        });
        bukkitCommandManager.getCommandContexts().registerContext(WorldType.class, context -> {
            final String arg = context.popFirstArg();
            return WorldType.fromString(arg).orElse(null);
        });
        bukkitCommandManager.getCommandContexts().registerContext(HyperWorld.class, context -> {
            final HyperWorld hyperWorld = worldManager.getWorld(context.popFirstArg());
            if (hyperWorld == null) {
                MessageUtil.sendMessage(context.getSender(), Messages.messageNoSuchWorld);
            }
            return hyperWorld;
        });
        bukkitCommandManager.getCommandContexts().registerContext(GameRule.class, context ->
            GameRule.getByName(context.popFirstArg()));
        bukkitCommandManager.getCommandContexts().registerContext(WorldFlag.class, context ->
            this.globalFlagContainer.getFlagFromString(context.popFirstArg().toLowerCase()));
        //noinspection deprecation
        bukkitCommandManager.enableUnstableAPI("help");
        bukkitCommandManager.registerCommand(this);
    }

    @HelpCommand public void doHelp(final CommandSender sender, final CommandHelp commandHelp) {
        commandHelp.showHelp();
    }

    @Subcommand("create") @Syntax(
        "<world> [generator: plugin name, vanilla][:[args]] [type: overworld, nether, end] [seed]"
            + " [generate-structures: true, false] [settings...]")
    @CommandPermission("hyperverse.create") @Description("Create a new world")
    @CommandCompletion("@null @generators @worldtypes @null @null true|false @null")
    public void createWorld(final CommandSender sender, final String world, String generator,
        @Default("overworld") final WorldType type, @Optional final Long specifiedSeed,
        @Default("true") final boolean generateStructures, @Default final String settings) {
        final long seed = specifiedSeed == null ? SeedUtil.randomSeed() : specifiedSeed;
        // Check if the name already exists
        for (final HyperWorld hyperWorld : this.worldManager.getWorlds()) {
            if (hyperWorld.getConfiguration().getName().equalsIgnoreCase(world)) {
                MessageUtil.sendMessage(sender, Messages.messageWorldExists);
                return;
            }
        }
        // Double check that Bukkit doesn't have the world stored
        if (Bukkit.getWorld(world) != null) {
            MessageUtil.sendMessage(sender, Messages.messageWorldExists);
            return;
        }
        // Now validate the world name
        if (!WorldUtil.validateName(world)) {
            MessageUtil.sendMessage(sender, Messages.messageWorldNameInvalid);
            return;
        }

        String generatorArgs = "";
        if (generator.contains(":")) {
            final String[] split = generator.split(":");
            generator = split[0];
            generatorArgs = split[1];
        }

        // Check if the generator is actually valid
        final WorldConfiguration worldConfiguration =
            WorldConfiguration.builder().setName(world).setGenerator(generator).setType(type).setSeed(seed)
                .setGenerateStructures(generateStructures).setSettings(settings)
                .setGeneratorArg(generatorArgs).createWorldConfiguration();
        final HyperWorld hyperWorld =
            hyperWorldFactory.create(UUID.randomUUID(), worldConfiguration);
        MessageUtil.sendMessage(sender, Messages.messageWorldCreationStarted);
        hyperWorld.sendWorldInfo(sender);

        // Make sure we don't detect the world load
        this.worldManager.ignoreWorld(world);

        try {
            hyperWorld.createBukkitWorld();
            // Register the world
            this.worldManager.addWorld(hyperWorld);
            MessageUtil.sendMessage(sender, Messages.messageWorldCreationFinished);
            if (sender instanceof Player) {
                // Attempt to teleport them to the world
                doTeleport((Player) sender, hyperWorld);
            }
        } catch (final HyperWorldValidationException validationException) {
            switch (validationException.getValidationResult()) {
                case UNKNOWN_GENERATOR:
                    MessageUtil.sendMessage(sender, Messages.messageGeneratorInvalid,
                        "%world%", hyperWorld.getConfiguration().getName(),
                        "%generator%", hyperWorld.getConfiguration().getGenerator());
                    break;
                case SUCCESS:
                    break;
                default:
                    MessageUtil.sendMessage(sender, Messages.messageCreationUnknownFailure);
                    break;
            }
        } catch (final Exception e) {
            MessageUtil.sendMessage(sender, Messages.messageWorldCreationFailed,
                "%reason%", e.getMessage());
        }
    }

    @Subcommand("import") @CommandPermission("hyperverse.import") @CommandAlias("hvimport")
    @CommandCompletion("@import-candidates @generators ") @Description("Load a world as a hyperworld")
    public void importWorld(final CommandSender sender, final String worldName, final String generator) {
        if (worldManager.getWorld(worldName) != null) {
            MessageUtil.sendMessage(sender, Messages.messageWorldAlreadyImported);
            return;
        }
        if (!WorldUtil.isSuitableImportCandidate(worldName, worldManager)) {
            MessageUtil.sendMessage(sender, Messages.messageNoSuchWorld);
            return;
        }
        worldManager.ignoreWorld(worldName); //Make sure we don't auto register on init
        final HyperWorld hyperWorld = hyperWorldFactory.create(UUID.randomUUID(),
            new WorldConfigurationBuilder().setName(worldName).setGenerator(generator)
                .createWorldConfiguration());
        final World bukkitWorld;
        try {
            hyperWorld.createBukkitWorld();
            bukkitWorld = hyperWorld.getBukkitWorld();
            assert bukkitWorld != null;
        } catch (HyperWorldValidationException e) {
            MessageUtil.sendMessage(sender, Messages.messageWorldImportFailure, "%reason%",
                e.getMessage());
            return;
        }
        worldManager.addWorld(hyperWorld);
        MessageUtil.sendMessage(sender, Messages.messageWorldImportFinished);
        if (sender instanceof Player) {
            //Schedule teleport 1-tick later so the world has a chance to load.
            Bukkit.getScheduler().runTaskLater(Hyperverse.getPlugin(Hyperverse.class),
                () -> doTeleport((Player) sender, worldManager.getWorld(bukkitWorld)), 1L);
        }
    }

    @Subcommand("list|l|worlds") @CommandPermission("hyperverse.list") @CommandAlias("hvl")
    @Description("List hyperverse worlds") public void doList(final CommandSender sender) {
        MessageUtil.sendMessage(sender, Messages.messageListHeader);
        Stream<HyperWorld> stream = this.worldManager.getWorlds().stream().sorted(Comparator.comparing(world -> world.getConfiguration().getName()));
        if (sender instanceof Entity) {
            stream = stream.sorted(Comparator
                .comparing(world -> !((Entity) sender).getWorld().equals(world.getBukkitWorld())));
        }
        stream.forEachOrdered(hyperWorld -> {
            final WorldConfiguration configuration = hyperWorld.getConfiguration();

            // Format the generator name a little better
            String generator = configuration.getGenerator();
            if (generator.isEmpty()) {
                generator = "vanilla";
            } else {
                generator = generator.toLowerCase();
            }

            final String loadStatus;
            if (hyperWorld.isLoaded()) {
                loadStatus = "<green><hover:show_text:\"<gray>Click to unload</gray>\"><click:run_command:/hyperverse unload "
                    + configuration.getName() + ">loaded</click></hover></green>";
            } else {
                loadStatus = "<red><hover:show_text:\"<gray>Click to load</gray>\"><click:run_command:/hyperverse load "
                    + configuration.getName() + ">unloaded</click></hover></red>";
            }

            final Message message;
            if (sender instanceof Entity && ((Entity) sender).getWorld() == hyperWorld.getBukkitWorld()) {
                message = Messages.messageListEntryCurrentWorld;
            } else {
                message = Messages.messageListEntry;
            }

            MessageUtil.sendMessage(sender, message, "%name%", configuration.getName(),
                "%generator%", generator, "%type%", configuration.getType().name(), "%load_status%", loadStatus);
        });
    }

    @Subcommand("teleport|tp") @CommandAlias("hvtp") @CommandPermission("hyperverse.teleport")
    @CommandCompletion("@hyperworlds:player=not_in,state=loaded") @Description("Teleport between hyperverse worlds")
    public void doTeleport(final Player player, final HyperWorld world) {
        if (world == null) {
            return;
        }
        if (!world.isLoaded()) {
            MessageUtil.sendMessage(player, Messages.messageWorldNotLoaded);
            return;
        }
        if (world.getBukkitWorld() == player.getWorld()) {
            MessageUtil.sendMessage(player, Messages.messageAlreadyInWorld);
            return;
        }
        int limit = world.getFlag(PlayerLimitFlag.class);
        assert world.getBukkitWorld() != null;
        if (limit >= world.getBukkitWorld().getPlayers().size()) {
            MessageUtil.sendMessage(player, Messages.messageWorldFull);
            return;
        }
        MessageUtil.sendMessage(player, Messages.messageTeleporting, "%world%",
            world.getConfiguration().getName());
        world.teleportPlayer(player);
    }

    @Subcommand("info|i") @CommandAlias("hvi") @CommandPermission("hyperverse.info")
    @CommandCompletion("@hyperworlds") @Description("View world info")
    public void doInfo(final CommandSender sender, final HyperWorld world) {
        if (world == null) {
            return;
        }
        MessageUtil.sendMessage(sender, Messages.messageInfoHeader);
        world.sendWorldInfo(sender);
    }

    @Subcommand("unload") @CommandPermission("hyperverse.unload")
    @CommandCompletion("@hyperworlds:state=loaded") @Description("Unload a world")
    public void doUnload(final CommandSender sender, final HyperWorld world) {
        if (world == null) {
            return;
        }
        if (!world.isLoaded()) {
            MessageUtil.sendMessage(sender, Messages.messageWorldNotLoaded);
            return;
        }
        final HyperWorld.WorldUnloadResult worldUnloadResult = world.unloadWorld();
        if (worldUnloadResult == HyperWorld.WorldUnloadResult.SUCCESS) {
            MessageUtil.sendMessage(sender, Messages.messageWorldUnloaded);
        } else {
            MessageUtil.sendMessage(sender, Messages.messageWorldUnloadFailed,
                "%reason%", worldUnloadResult.getDescription());
        }
    }

    @Subcommand("load") @CommandPermission("hyperverse.load")
    @CommandCompletion("@hyperworlds:state=unloaded") @Description("Load a world")
    public void doLoad(final CommandSender sender, final HyperWorld world) {
        if (world == null) {
            return;
        }
        if (world.isLoaded()) {
            MessageUtil.sendMessage(sender, Messages.messageWorldAlreadyLoaded);
            return;
        }
        try {
            world.createBukkitWorld();
        } catch (final HyperWorldValidationException e) {
            MessageUtil.sendMessage(sender, Messages.messageWorldImportFailure,
                    "%world%", world.getConfiguration().getName(), "%result%", e.getMessage());
            return;
        }

        world.getConfiguration().setLoaded(true);
        world.saveConfiguration();

        MessageUtil.sendMessage(sender, Messages.messageWorldLoadedSuccessfully);
    }

    @Subcommand("find|where") @CommandPermission("hyperverse.find") @CommandAlias("hvf|hvfind")
    @CommandCompletion("@players") @Description("Find the current world for a player")
    //public void findPlayer(final CommandSender sender, final String... players) {
    public void findPlayer(final CommandSender sender, final String player) {
        //for (String player : players) {
            final Player bukkitPlayer = Bukkit.getPlayer(player);
            if (bukkitPlayer == null) {
                MessageUtil.sendMessage(sender, Messages.messageNoPlayerFound, "%name%", player);
                return;
            }
            final Location location = bukkitPlayer.getLocation();
            final DecimalFormat format = Messages.miscCoordinateDecimalFormat;
        MessageUtil
            .sendMessage(sender, Messages.messagePlayerCurrentWorld, "%player%", player, "%world%",
                bukkitPlayer.getWorld().getName(), "%x%",
                        format.format(location.getX()), "%y%", format.format(location.getY()),
                        "%z%", format.format(location.getZ()));
        //}
    }

    @Subcommand("who") @CommandPermission("hyperverse.who") @CommandAlias("hvwho")
    @CommandCompletion("@hyperworlds") @Description("Find the current players in a world")
    public void findPlayersPresent(final CommandSender sender, @Optional final String world) {
        if (world != null) {
            final World bukkitWorld = Bukkit.getWorld(world);
            if (bukkitWorld == null) {
                MessageUtil.sendMessage(sender, Messages.messageNoSuchWorld);
                return;
            }
            final DecimalFormat format = Messages.miscCoordinateDecimalFormat;
            if (bukkitWorld.getPlayers().isEmpty()) {
                MessageUtil.sendMessage(sender, Messages.messageNoPlayersInWorld, "%world%", world);
                return;
            }
            final StringBuilder players = new StringBuilder();
            for (final Player player : bukkitWorld.getPlayers()) {
                final Location location = player.getLocation();
                players.append(MessageUtil.format(Messages.messageListEntryPlayer.toString(), "%player%",
                    player.getDisplayName(), "%world%", world, "%x%",
                    format.format(location.getX()), "%y%",
                    format.format(location.getY()), "%z%",
                    format.format(location.getZ())));
            }
            MessageUtil.sendMessage(sender, Messages.messageListEntryWorld, "%players%", players.toString(),
                "%world%", bukkitWorld.getName());
        }
        else {
            for (final World bukkitWorld : Bukkit.getWorlds()) {
                findPlayersPresent(sender, bukkitWorld.getName());
            }
        }
    }

    @Subcommand("flag set") @CommandPermission("hyperverse.flag.set")
    @CommandCompletion("@hyperworlds @flags @flag") @Description("Set a world flag")
    public void doFlagSet(final CommandSender sender, final HyperWorld hyperWorld,
        final WorldFlag<?, ?> flag, final String value) {
        if (flag == null) {
            MessageUtil.sendMessage(sender, Messages.messageFlagUnknown);
            return;
        }
        try {
            hyperWorld.setFlag(flag, value);
        } catch (final FlagParseException e) {
            MessageUtil.sendMessage(sender, Messages.messageFlagParseError,
                "%flag%", e.getFlag().getName(), "%value%", e.getValue(), "%reason%", e.getErrorMessage());
            return;
        }
        MessageUtil.sendMessage(sender, Messages.messageFlagSet);
    }

    @Subcommand("flag remove") @CommandPermission("hyperverse.flag.set")
    @CommandCompletion("@hyperworlds @flags") @Description("Remove a world flag")
    public void doFlagRemove(final CommandSender sender, final HyperWorld hyperWorld, final WorldFlag<?, ?> flag) {
        if (flag == null) {
            MessageUtil.sendMessage(sender, Messages.messageFlagUnknown);
            return;
        }
        hyperWorld.removeFlag(flag);
        MessageUtil.sendMessage(sender, Messages.messageFlagRemoved);
    }

    @Subcommand("gamerule set") @CommandPermission("hyperverse.gamerule.set")
    @CommandCompletion("@hyperworlds @gamerules @gamerule") @Description("Set a world gamerule")
    public void doGameRuleSet(final CommandSender sender, final HyperWorld hyperWorld,
        final GameRule gameRule, final String value) {
        if (gameRule == null) {
            MessageUtil.sendMessage(sender, Messages.messageGameRuleUnknown);
            return;
        }
        if (!hyperWorld.isLoaded()) {
            MessageUtil.sendMessage(sender, Messages.messageWorldNotLoaded);
            return;
        }

        final Object object;
        if (gameRule.getType() == Boolean.class) {
            try {
                object = Boolean.parseBoolean(value);
            } catch (final Exception e) {
                MessageUtil.sendMessage(sender, Messages.messageGameRuleParseError);
                return;
            }
        } else if (gameRule.getType() == Integer.class) {
            try {
                object = Integer.parseInt(value);
            } catch (final Exception e) {
                MessageUtil.sendMessage(sender, Messages.messageGameRuleParseError);
                return;
            }
        } else {
            // ??
            return;
        }

        hyperWorld.getBukkitWorld().setGameRule(gameRule, object);
        MessageUtil.sendMessage(sender, Messages.messageGameRuleSet);
    }

    @Subcommand("gamerule remove") @CommandPermission("hyperverse.gamerule.set")
    @CommandCompletion("@hyperworlds @gamerules") @Description("Remove a world game rule")
    public void doGameRuleRemove(final CommandSender sender, final HyperWorld hyperWorld, final GameRule gameRule) {
        if (gameRule == null) {
            MessageUtil.sendMessage(sender, Messages.messageGameRuleUnknown);
            return;
        }
        if (!hyperWorld.isLoaded()) {
            MessageUtil.sendMessage(sender, Messages.messageWorldNotLoaded);
            return;
        }
        hyperWorld.getBukkitWorld().setGameRule(gameRule,
            hyperWorld.getBukkitWorld().getGameRuleDefault(gameRule));
        MessageUtil.sendMessage(sender, Messages.messageGameRuleRemoved);
    }

    @Subcommand("delete") @CommandPermission("hyperverse.delete")
    @CommandCompletion("@hyperworlds") @Description("Delete a world")
    public void doDelete(final CommandSender sender, final HyperWorld hyperWorld) {
        if (hyperWorld == null) {
            MessageUtil.sendMessage(sender, Messages.messageNoSuchWorld);
            return;
        }
        final HyperWorld.WorldUnloadResult worldUnloadResult = hyperWorld.deleteWorld();
        if (worldUnloadResult != HyperWorld.WorldUnloadResult.SUCCESS) {
            MessageUtil.sendMessage(sender, Messages.messageWorldNotRemoved, "%reason%"
                , worldUnloadResult.getDescription());
            return;
        }
        MessageUtil.sendMessage(sender, Messages.messageWorldRemoved);
    }

    @Subcommand("reload") @CommandPermission("hyperverse.reload") @CommandAlias("hvreload")
    @Description("Reload the Hyperverse configuration")
    public void doConfigReload(final CommandSender sender) {
      Hyperverse.getPlugin(Hyperverse.class).reloadConfiguration(sender);
    }

    @Subcommand("debugpaste") @CommandPermission("hyperverse.debugpaste")
    @Description("Create a debug paste. This will upload your configuration files to Athion. Beware.")
    public void doDebugPaste(final CommandSender sender) {
        this.taskChainFactory.newChain().async(() -> {
            try {
                final Hyperverse hyperverse = Hyperverse.getPlugin(Hyperverse.class);

                StringBuilder b = new StringBuilder();
                b.append(
                    "# Welcome to this paste\n# It is meant to provide us at IntellectualSites with better information about your "
                        + "problem\n\n");

                b.append("# Server Information\n");
                b.append("Server Version: ").append(Bukkit.getVersion()).append("\n");

                b.append("Plugins:");
                for (final Plugin plugin : Bukkit.getPluginManager().getPlugins()) {
                    b.append("\n  ").append(plugin.getName()).append(":\n    ").append("version: '")
                        .append(plugin.getDescription().getVersion()).append('\'').append("\n    enabled: ").append(plugin.isEnabled());
                }

                b.append("\n\n# YAY! Now, let's see what we can find in your JVM\n");
                Runtime runtime = Runtime.getRuntime();
                RuntimeMXBean rb = ManagementFactory.getRuntimeMXBean();
                b.append("Uptime: ").append(
                    TimeUnit.MINUTES.convert(rb.getUptime(), TimeUnit.MILLISECONDS) + " minutes")
                    .append('\n');
                b.append("JVM Flags: ").append(rb.getInputArguments()).append('\n');
                b.append("Free Memory: ").append(runtime.freeMemory() / 1024 / 1024 + " MB")
                    .append('\n');
                b.append("Max Memory: ").append(runtime.maxMemory() / 1024 / 1024 + " MB")
                    .append('\n');
                b.append("Java Name: ").append(rb.getVmName()).append('\n');
                b.append("Java Version: '").append(System.getProperty("java.version"))
                    .append("'\n");
                b.append("Java Vendor: '").append(System.getProperty("java.vendor")).append("'\n");
                b.append("Operating System: '").append(System.getProperty("os.name")).append("'\n");
                b.append("OS Version: ").append(System.getProperty("os.version")).append('\n');
                b.append("OS Arch: ").append(System.getProperty("os.arch")).append('\n');
                b.append("# Okay :D Great. You are now ready to create your bug report!");
                b.append(
                    "\n# You can do so at https://github.com/Sauilitired/Hyperverse/issues");
                b.append("\n# or via our Discord at https://discord.gg/KxkjDVg");

                // We use the PlotSquared profile
                final IncendoPaster incendoPaster = new IncendoPaster("plotsquared");
                incendoPaster.addFile(new IncendoPaster.PasteFile("information", b.toString()));

                try {
                    final File logFile = new File(Bukkit.getWorldContainer(), "./logs/latest.log");
                    if (Files.size(logFile.toPath()) > 14_000_000) {
                        throw new IOException("Too big...");
                    }
                    incendoPaster.addFile(new IncendoPaster.PasteFile("latest.log", IncendoPaster.readFile(logFile)));
                } catch (IOException ignored) {
                    MessageUtil.sendMessage(sender, Messages.messageLogTooBig);
                }

                try {
                    incendoPaster.addFile(new IncendoPaster.PasteFile("hyperverse.conf",
                        IncendoPaster.readFile(new File(hyperverse.getDataFolder(), "hyperverse.conf"))));
                } catch (final IllegalArgumentException | IOException ignored) {
                }


                for (final HyperWorld hyperWorld : worldManager.getWorlds()) {
                    incendoPaster.addFile(new IncendoPaster.PasteFile(String.format("%s.json",
                        hyperWorld.getConfiguration().getName()), IncendoPaster.readFile(this.worldManager.getWorldDirectory().
                        resolve(String.format("%s.json", hyperWorld.getConfiguration().getName())).toFile())));
                }

                try {
                    final String rawResponse = incendoPaster.upload();
                    final JsonObject jsonObject =
                        new JsonParser().parse(rawResponse).getAsJsonObject();

                    if (jsonObject.has("created")) {
                        final String pasteId = jsonObject.get("paste_id").getAsString();
                        final String link =
                            String.format("https://athion.net/ISPaster/paste/view/%s", pasteId);
                        MessageUtil.sendMessage(sender, Messages.messagePasteUpload, "%paste%", link);
                    } else {
                        final String responseMessage = jsonObject.get("response").getAsString();
                        MessageUtil.sendMessage(sender, Messages.messagePasteFailed, "%reason%", responseMessage);
                    }
                } catch (final Throwable throwable) {
                    throwable.printStackTrace();
                    MessageUtil.sendMessage(sender, Messages.messagePasteFailed, "%reason%", throwable.getMessage());
                }
            } catch (final Exception ignored) {
            }
        }).execute();
    }

}
