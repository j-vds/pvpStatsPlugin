package plstats;

import arc.*;
import arc.struct.ObjectIntMap;
import arc.struct.ObjectMap;
import arc.struct.Seq;
import arc.util.*;
import mindustry.Vars;
import mindustry.game.EventType;
import mindustry.game.EventType.*;
import mindustry.game.Team;
import mindustry.gen.*;
import mindustry.mod.Plugin;
import mindustry.net.Administration;
import mindustry.world.blocks.storage.CoreBlock;

public class pvpStats extends Plugin {
    private long minScoreTime;
    private long rageTime; // 45 seconds
    private long teamSwitchScore; // if you switch 30 seconds before your team loses a core you still get deducted some points

    public ObjectIntMap<String> playerPoints; // UUID - INTEGER
    public ObjectMap<String, timePlayerInfo> playerInfo;
    public dataStorage dS;

    //private ObjectMap<Player, Team> rememberSpectate = new ObjectMap<>();

    //register event handlers and create variables in the constructor
    public pvpStats(){
        dS = new dataStorage();
        playerPoints = dS.getData();
        playerInfo = new ObjectMap<>();
        loadTimings(dS.getTimings());

        Events.on(PlayerConnect.class, event ->{
        //change the name of the player
            int points = playerPoints.get(event.player.uuid(),0);
            event.player.name(String.format("[sky]%d [][white]#[] %s", points, event.player.name()));
        });

        Events.on(PlayerJoin.class, event -> {
            //save the timer!
            event.player.sendMessage("[gold] Beta version[][white] - The number before your name equals your PVP score[]");
            playerInfo.put(event.player.uuid(), new timePlayerInfo(event.player));
        });

        Events.on(PlayerLeave.class, event -> {
            //save the timer!
            timePlayerInfo tpi = playerInfo.get(event.player.uuid());
            tpi.left();
            //check if player played long enough
            if(!tpi.canUpdate(minScoreTime)){
               playerInfo.remove(event.player.uuid());
            }
        });

        //detect if player changes team via a chatcommand
        Events.on(PlayerChatEvent.class, event ->{
            //if(event.message.split("\\s+")[0].equals("/team")){
                playerInfo.get(event.player.uuid()).teamChange(event.player.team());
            //}
        });

        Events.on(EventType.BlockDestroyEvent.class, event -> {
           if(event.tile.build instanceof CoreBlock.CoreBuild && !Vars.state.gameOver){
               Log.info("Core destroyed @ ", event.tile.build.team);
               if(event.tile.build.team.cores().size <= 1){
                   Call.sendMessage(String.format("[gold] %s lost...", event.tile.build.team.name));
                   //other player get a point
                   updatePoints(event.tile.team(), -1, 1, false);

               }
           }
        });

        Events.on(WorldLoadEvent.class, event -> {
            Timer.schedule(()->{
                for(Player p: Groups.player.copy(new Seq<>())){
                    playerInfo.get(p.uuid()).setTeam(p.team());
                }
            }, 1.5f);
        });

        Events.on(EventType.GameOverEvent.class, event -> {
            Log.info(String.format("Winner %s",event.winner));
            //new map
            //updatePoints(event.winner, 1, -1, true);
            //update the JSON file
            Core.app.post(() -> dS.writeData(playerPoints));
            //clear player history after 3 seconds
            Timer.schedule(() -> playerInfo.values().forEach(pt -> pt.reset()),3f);
        });
    }

    //register commands that run on the server
    @Override
    public void registerServerCommands(CommandHandler handler){
        handler.register("writestats", "update the json stats file", (args)->{
           Core.app.post(() -> dS.writeData(playerPoints));
        });

        handler.register("pvp_timers","[update/show]", "update the timings", (args)->{
            if(args.length == 1) {
                if(args[0].equals("update")){
                    Log.info("<pvpStats> updating timings...");
                    loadTimings(dS.reloadTimings());
                }
            }
            Log.info("ragetime: @ seconds", (int)(rageTime/1000L));
            Log.info("minScoreTime: @ seconds", (int)(minScoreTime/1000L));
            Log.info("teamSwitchScore: @ seconds", (int)(teamSwitchScore/1000L));

        });

    }

    //register commands that player can invoke in-game
    @Override
    public void registerClientCommands(CommandHandler handler){
        /*
        handler.<Player>register("lb", "show top 10", (args, player) -> {
            StringBuilder sb = new StringBuilder();
            sb.append("[gold]--- Standings ---[]\n");
            ObjectMap<Integer, Seq<String>> sortMap = new ObjectMap<>();
            Seq<String> tussenresult;
            for(String uuid: playerPoints.keys()){
                tussenresult = sortMap.get(playerPoints.get(uuid), new Seq<>());
                tussenresult.add(uuid);
                sortMap.put(playerPoints.get(uuid), tussenresult);
            }
            Seq<Integer> sortedValues = sortMap.keys().toSeq().sort();
            int count = 1;
            for(int i : sortedValues){
                Log.info(i);
                for(String s:sortMap.get(i)){
                    Log.info(s);
                    sb.append("[green]").append(count).append("[][white] : []").append(playerInfo.get(s).name()).append("\n");
                    count++;
                    if(count > 10){break;}
                }
                if(count > 10){break;}
            }
            Call.infoMessage(player.con(), sb.toString());

        });

         */
    }

    private void loadTimings(ObjectMap<String, Long> timings){
        minScoreTime = timings.get("minScoreTime");
        rageTime = timings.get("rageTime");
        teamSwitchScore = timings.get("teamSwitchScore");
    }

    private void updatePoints(Team selectTeam, int addSelect, int addOther, boolean write){
        Seq<Team> validTeams = new Seq<>();
        timePlayerInfo t;
        Seq<String> tbremoved = new Seq<>();
        for(String uuid: playerInfo.keys()){
            t = playerInfo.get(uuid);
            //long enough on the server
            if(!t.canUpdate(minScoreTime)){
                System.out.println("cant update");
                continue;
            }
            // if the player left
            if(!t.rageQuit(rageTime) && !t.playing){
                System.out.println("left");
                tbremoved.add(uuid);
                //playerInfo.remove(uuid);
                continue;
            }

            if(t.team() == selectTeam){
                System.out.println("loser points");
                playerPoints.put(uuid, playerPoints.get(uuid,0)+addSelect);
            }else{
                //check evasion!
                if(t.teamSwitchEvade(teamSwitchScore, selectTeam)){
                    Log.info("<pvpStats> player @ changed teams to get points ...", t.name());
                    playerPoints.put(uuid, playerPoints.get(uuid,0)+addSelect);
                }else{
                    if(validTeams.contains(t.team())) {
                        playerPoints.put(uuid, playerPoints.get(uuid,0)+addOther);
                        System.out.println("winner points");
                    }else{
                        //check if valid
                        if(t.team().cores().size > 0){
                            System.out.println("winner points");
                            validTeams.add(t.team());
                            playerPoints.put(uuid, playerPoints.get(uuid, 0)+addOther);
                        }
                    }

                }
            }
            // UPDATE PLAYER NAMES
            String oldName = t.name().substring(t.name().indexOf("#")+1);
            t.name(String.format("[sky]%d [][white]#[]%s", playerPoints.get(uuid, 0), oldName));
        }
        tbremoved.forEach(u -> playerInfo.remove(u));

        if(write){
            Core.app.post(() -> dS.writeData(playerPoints));
        }
    }
}
