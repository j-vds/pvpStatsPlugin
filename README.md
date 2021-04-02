# PVP stats
*compatible with the teamPlugin*
<br/> Originally made for the mindustry.pl server.

### Concept
If a team is lost all the other teams get a point. If your core gets destroyed, you will lose a point.
![](https://github.com/J-VdS/pvpStatsPlugin/blob/master/name.PNG)<br/>
The amount of points you have will be printed in front of your name.
 
If a gameover occurs all the data will be written to a .json file.

### Terminal commands
* `writestats` --> dumps the data in a .json file
* `pvp_timers [update/show]` --> shows the timers
#### Timers (all in ms)
* `rageTime`: if a player is about to lose and leaves (ragequits) he still can lose points. (If a player leaves but was about to win will gain a point.) 
* `minScoreTime`: a player needs to play at least this amount of time to be able to gain/lose points.
* `teamSwitchScore`: if a player changes teams to get some points or prevent to lose some points he could change teams. BUT: in this time period he will still be able to get points deducted!

### Commands
`/lb` This will show the top 10 including your current position.

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

