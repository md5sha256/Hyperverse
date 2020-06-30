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

package se.hyperver.hyperverse.flags.implementation;

import se.hyperver.hyperverse.configuration.Messages;
import se.hyperver.hyperverse.flags.FlagParseException;
import se.hyperver.hyperverse.flags.WorldFlag;
import org.jetbrains.annotations.NotNull;

import java.util.regex.Pattern;

public class WorldPermissionFlag extends WorldFlag<String, WorldPermissionFlag> {

    private static final Pattern permissionPattern = Pattern.compile("[A-Za-z0-9\\-_.]+");
    public static final WorldPermissionFlag WORLD_PERMISSION_FLAG_DEFAULT = new WorldPermissionFlag("");

    public WorldPermissionFlag(@NotNull final String value) {
        super(value, Messages.flagDescriptionWorldPermission);
    }

    @Override public WorldPermissionFlag parse(@NotNull final String input) throws
        FlagParseException {
        if (input.isEmpty()) {
            return WORLD_PERMISSION_FLAG_DEFAULT;
        }
        if (permissionPattern.matcher(input).matches()) {
            return flagOf(input);
        }
        throw new FlagParseException(this, input, "A permission node may only contain alphanumerical characters,"
            + " -, . and _");
    }

    @Override public WorldPermissionFlag merge(@NotNull final String newValue) {
        return flagOf(newValue);
    }

    @Override public String toString() {
        return getValue();
    }

    @Override public String getExample() {
        return "your.permission.node";
    }

    @Override protected WorldPermissionFlag flagOf(@NotNull final String value) {
        return new WorldPermissionFlag(value);
    }

    @Override public @NotNull String getValueAsString() {
        return getValue();
    }
}
