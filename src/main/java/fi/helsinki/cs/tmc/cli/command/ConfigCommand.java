package fi.helsinki.cs.tmc.cli.command;

import com.google.common.base.Optional;
import fi.helsinki.cs.tmc.cli.backend.SettingsIo;
import fi.helsinki.cs.tmc.cli.core.AbstractCommand;
import fi.helsinki.cs.tmc.cli.core.CliContext;
import fi.helsinki.cs.tmc.cli.core.Command;
import fi.helsinki.cs.tmc.cli.core.CommandFactory;
import fi.helsinki.cs.tmc.cli.io.Io;

import fi.helsinki.cs.tmc.cli.utils.BadValueTypeException;
import fi.helsinki.cs.tmc.cli.utils.PropertyFunctions;
import fi.helsinki.cs.tmc.core.domain.ProgressObserver;
import fi.helsinki.cs.tmc.core.holders.TmcSettingsHolder;
import fi.helsinki.cs.tmc.core.utilities.TmcServerAddressNormalizer;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;

import java.util.*;

@Command(name = "config", desc = "Set/unset TMC-CLI properties and change settings")
public class ConfigCommand extends AbstractCommand {

    private CliContext context;
    private Io io;
    private static final Map<String, PropertyFunctions> ALLOWED_KEYS = new HashMap<>();
    private static final Set<String> PROGRESS_BAR_COLORS = new HashSet<>(Arrays.asList("black", "red", "green", "blue", "yellow", "blue", "purple", "cyan", "white", "none"));
    private static final String serverAddressKey = "server-address";
    private static final String testResultRightKey = "testresults-right";
    private static final String testResultLeftKey = "testresults-left";
    private static final String progressBarLeftKey = "progressbar-left";
    private static final String progressBarRightKey = "progressbar-right";
    private static final String sendDiagnosticsKey = "send-diagnostics";

    private HashMap<String, String> properties;
    private boolean quiet;

    public ConfigCommand() {
        configureAllowedKeys();
    }

    private void configureAllowedKeys() {
        // add new possible config options here
        // create an anonymous class which determines where the value is stored
        // some values are stored in settings, some in properties
        ALLOWED_KEYS.put(sendDiagnosticsKey, new PropertyFunctions() {
            @Override
            public String getter() {
                return Boolean.toString(context.getSettings().getSendDiagnostics());
            }

            @Override
            public void setter(String value) throws BadValueTypeException {
                boolean send = getBooleanSendValue(value);
                context.getSettings().setSendDiagnostics(send);
                SettingsIo.saveCurrentSettingsToAccountList(context.getSettings());
            }
        });

        ALLOWED_KEYS.put(serverAddressKey, new PropertyFunctions() {
            @Override
            public String getter() {
                return context.getSettings().getServerAddress();
            }

            @Override
            public void setter(String value) throws BadValueTypeException {
                String addr = value;
                if (!addr.matches("^https?://.*")) {
                    throw new BadValueTypeException("Please start the address with http[s]://");
                }
                context.getSettings().setServerAddress(addr);
                normalizeServerAddress();
                SettingsIo.saveCurrentSettingsToAccountList(context.getSettings());
                SettingsIo.delete();
                io.println("You have been logged out.");
                io.println("Please login again to use the new server.");
                LoginCommand loginCommand = new LoginCommand();
                while (!loginCommand.login(context, null, Optional.of(value))) {
                    io.println("Please login again.");
                }
            }
        });
        ALLOWED_KEYS.put(testResultRightKey, new PropertyFunctions() {
            @Override
            public String getter() {
                return context.getProperties().get(testResultRightKey);
            }

            @Override
            public void setter(String value) throws BadValueTypeException {
                addBarColorToProperties(testResultRightKey, value);
            }
        });
        ALLOWED_KEYS.put(testResultLeftKey, new PropertyFunctions() {
            @Override
            public String getter() {
                return context.getProperties().get(testResultLeftKey);
            }

            @Override
            public void setter(String value) throws BadValueTypeException {
                addBarColorToProperties(testResultLeftKey, value);
            }
        });
        ALLOWED_KEYS.put(progressBarLeftKey, new PropertyFunctions() {
            @Override
            public String getter() {
                return context.getProperties().get(progressBarLeftKey);
            }

            @Override
            public void setter(String value) throws BadValueTypeException {
                addBarColorToProperties(progressBarLeftKey, value);
            }
        });
        ALLOWED_KEYS.put(progressBarRightKey, new PropertyFunctions() {
            @Override
            public String getter() {
                return context.getProperties().get(progressBarRightKey);
            }

            @Override
            public void setter(String value) throws BadValueTypeException {
                addBarColorToProperties(progressBarRightKey, value);
            }
        });
    }

    private boolean getBooleanSendValue(String value) throws BadValueTypeException {
        isBooleanValue(value);
        return Boolean.parseBoolean(value);
    }

    private void isBooleanValue(String newVal) throws BadValueTypeException {
        if (!newVal.trim().toLowerCase().equals("true") && !newVal.trim().toLowerCase().equals("false")) {
            throw new BadValueTypeException("Please write either true or false");
        }
    }

    @Override
    public String[] getUsages() {
        return new String[] {
            "[-q|--quiet] KEY=\"VALUE\"...",
            "-d|--delete [-q|--quiet] KEY...",
            "-l|--list",
            "-g|--get=KEY"};
    }

    @Override
    public void getOptions(Options options) {
        options.addOption("g", "get", true, "Get value of a key");
        options.addOption("d", "delete", false, "Unset given property keys");
        options.addOption("q", "quiet", false, "Don't ask confirmations");
        options.addOption("l", "list", false, "List all properties");
    }

    @Override
    public void run(CliContext context, CommandLine args) {
        this.context = context;
        this.io = context.getIo();

        boolean get = args.hasOption("g");
        boolean listing = args.hasOption("l");
        boolean delete = args.hasOption("d");
        boolean update = !get && !listing && !delete;
        this.quiet = args.hasOption("q");

        String[] arguments = args.getArgs();
        arguments = Arrays.stream(arguments).filter(o -> !o.trim().isEmpty()).toArray(String[]::new);
        this.properties = context.getProperties();

        this.context.getAnalyticsFacade().saveAnalytics("config");

        if ((get ? 1 : 0) + (listing ? 1 : 0) + (delete ? 1 : 0) > 1) {
            io.errorln("Only one of the --get or --list or --delete options can "
                    + "be used at same time.");
            printUsage(context);
            return;
        }

        if (listing) {
            if (arguments.length != 0) {
                io.errorln("Listing option doesn't take any arguments.");
                printUsage(context);
                return;
            }
            printAllProperties();
            return;
        }

        if (get) {
            if (arguments.length != 0) {
                io.errorln("There should not be extra arguments when using --get option.");
                printUsage(context);
                return;
            }
            String key = args.getOptionValue('g');
            boolean exists = ALLOWED_KEYS.containsKey(key);
            if (!exists && !quiet) {
                io.errorln("The property " + key + " doesn't exist.");
                return;
            }
            String value = ALLOWED_KEYS.get(key).getter();
            io.println(exists ? (value != null ? value : "no value set") : "");
            return;
        } else if (delete) {
            deleteProperties(arguments);
        } else {
            setProperties(arguments);
        }
        context.saveProperties();
    }

    private void printAllProperties() {
        ALLOWED_KEYS.entrySet()
                    .stream()
                    .sorted(Comparator.comparing(Map.Entry::getKey))
                    .forEach(e -> {
                        String key = e.getKey();
                        String value = e.getValue().getter();
                        io.println(key + "=" + ( value != null ? value: "<no value set>" ));
                    });
    }

    private void deleteProperties(String[] keys) {
        if (this.quiet) {
            for (String key : keys) {
                if (ALLOWED_KEYS.keySet().contains(key) && properties.containsKey(key)) {
                        properties.remove(key);
                }
            }
            return;
        }

        if (keys.length == 0) {
            io.errorln("Expected at least one property as argument.");
            printUsage(context);
            return;
        }

        for (String key : keys) {
            if (!properties.containsKey(key) || !ALLOWED_KEYS.keySet().contains(key)) {
                io.error("Key " + key + " doesn't exist or cannot be removed.");
                return;
            }
        }

        io.println("Deleting " + keys.length + " properties.");

        if (!io.readConfirmation("Are you sure?", true)) {
            return;
        }
        for (String key : keys) {
            String oldValue = properties.remove(key);
            io.println("Deleted key " + key + ", was " + oldValue);
        }
    }

    private void setProperties(String[] arguments) {
        if (arguments.length == 0 || !arguments[0].contains("=")) {
            io.errorln("Expected at least one key-value pair.");
            printUsage(context);
            return;
        }
        
        if (this.quiet) {
            setPropertiesQuietly(arguments);
            return;
        }

        io.println("Setting property keys:");
        for (String argument : arguments) {
            String[] parts = argument.split("=", 2);
            if (parts.length < 2) {
                continue;
            }
            if (!checkIfAllowedKey(parts[0])) {
                continue;
            }
            String oldValue = properties.get(parts[0]);
            if (parts[0].equals(serverAddressKey)) {
                io.println("All courses are now hosted at https://tmc.mooc.fi. We do not advise changing the server address.");
                if (parts[1].contains("tmc.mooc.fi/mooc")) {
                    io.println("The server https://tmc.mooc.fi/mooc is no longer supported by this client.\n" +
                            "If you'd like to do the migrated courses, you'll have to create an account on the new server.\n" +
                            "Choose the MOOC organization when logging in.\n\n" +
                            "For more information, check the course materials on mooc.fi.");
                    return;
                }
            }
            if (io.readConfirmation(" Set " + parts[0] + " to \"" + parts[1] + "\"?", true)) {
                if (!saveValue(parts[0], parts[1])) {
                    continue;
                }
                io.print("Property " + parts[0] + " is now \"" + parts[1] + "\"");
                if (oldValue != null) {
                    io.println(", was \"" + oldValue + "\".");
                } else {
                    io.println(".");
                }
            }

        }
        io.println();
    }

    private void setPropertiesQuietly(String[] arguments) {
        for (String argument : arguments) {
            String[] parts = argument.split("=", 2);
            if (checkIfAllowedKey(parts[0])) {
                saveValue(parts[0], parts[1]);
            }
        }
    }

    private boolean saveValue(String key, String value) {
        try {
            ALLOWED_KEYS.get(key).setter(value);
        } catch (Exception e) {
            io.errorln(e.getMessage());
            return false;
        }
        return true;
    }

    private boolean checkIfAllowedKey(String key) {
        if (!ALLOWED_KEYS.keySet().contains(key)) {
            io.println("\"" + key + "\" is not an allowed key.");
            io.println("Allowed keys are: ");
            ALLOWED_KEYS.keySet().forEach(k -> io.print(" " + k + '\n'));
            return false;
        }
        return true;
    }

    private void addBarColorToProperties(String key, String color) throws BadValueTypeException {
        if (!PROGRESS_BAR_COLORS.contains(color)) {
            throw new BadValueTypeException("Color " + color + " not supported.");
        }
        properties.put(key, color);
        SettingsIo.saveProperties(properties);
    }

    private boolean normalizeServerAddress() {
        TmcServerAddressNormalizer normalizer = new TmcServerAddressNormalizer();
        normalizer.normalize();
        try {
            this.context.getTmcCore().authenticate(ProgressObserver.NULL_OBSERVER, TmcSettingsHolder.get().getPassword().get()).call();
        } catch (Exception e) {
            return false;
        }
        normalizer.selectOrganizationAndCourse();
        return true;
    }
}
