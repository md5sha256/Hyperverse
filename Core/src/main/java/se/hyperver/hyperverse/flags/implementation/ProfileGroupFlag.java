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
import se.hyperver.hyperverse.util.WorldUtil;
import org.jetbrains.annotations.NotNull;

public class ProfileGroupFlag extends WorldFlag<String, ProfileGroupFlag> {

    public static final ProfileGroupFlag PROFILE_GROUP_FLAG_EMPTY = new ProfileGroupFlag("default");

    private ProfileGroupFlag(@NotNull final String group) {
        super(group, Messages.flagDescriptionProfileGroup);
    }

    @Override public ProfileGroupFlag parse(@NotNull String input) throws FlagParseException {
        if (WorldUtil.validateName(input)) {
            return flagOf(input);
        }
        throw new FlagParseException(this, input, "A world name may only contain (up to) 16 alphanumerical characters, - and _");
    }

    @Override public ProfileGroupFlag merge(@NotNull String newValue) {
        return flagOf(newValue);
    }

    @Override public String toString() {
        return this.getValue();
    }

    @Override public String getExample() {
        return "survival_worlds";
    }

    @Override protected ProfileGroupFlag flagOf(@NotNull final String value) {
        return new ProfileGroupFlag(value);
    }

    @Override public @NotNull String getValueAsString() {
        return getValue();
    }
}
