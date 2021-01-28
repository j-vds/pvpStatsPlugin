# PVP stats
*compatible with the teamPlugin*
<br/> Originally made for the mindustry.pl server.

### Concept
IMAGE


### Terminal commands
* `writestats` --> dumps the data in a .json file
* `pvp_timers [update/show]` --> shows the timers
#### Timers (all in ms)
* `rageTime`: if a player is about to lose and leaves (ragequits) he still can lose points. (If a player leaves but was about to win will gain a point.) 

### Commands
There are no commands for normal players.

### Admin Only commands
There are no commands for admins.


### Feedback
Open an issue if you have a suggestion.

### Releases
Prebuild relases can be found [here](https://github.com/J-VdS/pvpStatsPlugin/releases)

### Server which isn't allowed to use the plugin
mindustry.ru

### info/help
You can always open an issue or contact me on discord: Fishbuilder#4502

### Building a Jar 

`gradlew jar` / `./gradlew jar`

Output jar should be in `build/libs`.


### Installing

Simply place the output jar from the step above in your server's `config/mods` directory and restart the server.
List your currently installed plugins by running the `plugins` command.

