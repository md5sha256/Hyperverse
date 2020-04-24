
//Hyperverse - A minecraft world management plugin
//
//    This program is free software: you can redistribute it and/or modify
//    it under the terms of the GNU General Public License as published by
//    the Free Software Foundation, either version 3 of the License, or
//    (at your option) any later version.
//
//    This program is distributed in the hope that it will be useful,
//    but WITHOUT ANY WARRANTY; without even the implied warranty of
//    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//    GNU General Public License for more details.
//
//    You should have received a copy of the GNU General Public License
//    along with this program. If not, see <http://www.gnu.org/licenses/>.

package se.hyperver.hyperverse.flags.implementation;

import org.jetbrains.annotations.NotNull;
import se.hyperver.hyperverse.configuration.Messages;
import se.hyperver.hyperverse.flags.FlagParseException;
import se.hyperver.hyperverse.flags.WorldFlag;

/**
 * Flag relating to per-world player limits.
 */
public class PlayerLimitFlag extends WorldFlag<Integer, PlayerLimitFlag> {

    public static final PlayerLimitFlag NO_LIMIT_FLAG = new PlayerLimitFlag(0);

    private PlayerLimitFlag(@NotNull final Integer value) {
        super(value, Messages.flagDescriptionPlayerLimit);
    }

    @Override public PlayerLimitFlag parse(@NotNull final String input) throws FlagParseException {
        try {
            return flagOf(Integer.parseInt(input));
        } catch (IllegalArgumentException ex) {
            throw new FlagParseException(this, input, "A player limit must be a non-negative integer");
        }
    }

    @Override public PlayerLimitFlag merge(@NotNull final Integer newValue) {
        return flagOf(newValue);
    }

    @Override public String toString() {
        return this.getValue().toString();
    }

    @Override public String getExample() {
        return "10";
    }

    @Override protected PlayerLimitFlag flagOf(@NotNull final Integer value) {
        if (value < 1) {
            throw new IllegalArgumentException("Limit must be a non-negative!");
        }
        return new PlayerLimitFlag(value);
    }
}
