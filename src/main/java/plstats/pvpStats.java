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
import mindustry.world.blocks.storage.CoreBlock;

import java.util.Comparator;

public class pvpStats extends Plugin {
    private static boolean DEBUGPRINT = false;
    private debugPrinter printer;

    private long minScoreTime;
    private long rageTime; // 45 seconds
    private long teamSwitchScore; // if you switch 30 seconds before your team loses a core you still get deducted some points

    private ObjectIntMap<String> playerPoints; // UUID - INTEGER
    private ObjectMap<String, timePlayerInfo> playerInfo;
    private ObjectMap<String, String> uuidBackUp;
    private dataStorage dS;

    public Seq<TTriple<String, Integer, String>> lb_array = new Seq<>();
    private int TOPPLAYERAMOUNT = 10;

    //private ObjectMap<Player, Team> rememberSpectate = new ObjectMap<>();

    //register event handlers and create variables in the constructor
    public pvpStats(){
        dS = new dataStorage();
        playerPoints = dS.getData();
        playerInfo = new ObjectMap<>();
        uuidBackUp = new ObjectMap<>();
        loadTimings(dS.getTimings());

        //printcalls
        this.printer = new debugPrinter(DEBUGPRINT);
        //before player joins
        Events.on(PlayerConnect.class, event ->{
        //change the name of the player
            int points = playerPoints.get(event.player.uuid(),0);
            event.player.name(String.format("[sky]%d [][white]#[] %s", points, event.player.name()));
        });

        //players joins
        Events.on(PlayerJoin.class, event -> {
            //save the timer!
            event.player.sendMessage("[gold] Beta version[][white] - The number before your name equals your PVP score[]");
            event.player.sendMessage("[sky] --> pvpStatsPlugin[]");
            playerInfo.put(event.player.uuid(), new timePlayerInfo(event.player));

            uuidBackUp.put(Strings.stripColors(event.player.name().substring(event.player.name().indexOf("#")+1)), event.player.uuid());
        });

        Events.on(PlayerLeave.class, event -> {
            //save the timer!
            timePlayerInfo tpi = playerInfo.get(event.player.uuid());
            if(tpi == null){
                tpi = changedUUID(event.player);
                if(tpi == null){
                    Log.info("<pvpStats> UUID backup failed");
                    return;
                }
            }
            tpi.left();
            //check if player played long enough
            if(!tpi.canUpdate(minScoreTime)){
               playerInfo.remove(event.player.uuid());
            }

            uuidBackUp.remove(Strings.stripColors(event.player.name().substring(event.player.name().indexOf("#")+1)));

            //check array of players that recently left?

        });

        //detect if player changes team via a chatcommand
        Events.on(PlayerChatEvent.class, event ->{
            //if(event.message.split("\\s+")[0].equals("/team")){
                playerInfo.get(event.player.uuid()).teamChange(event.player.team());
            //}
        });

        Events.on(EventType.BlockDestroyEvent.class, event -> {
           if(event.tile.build instanceof CoreBlock.CoreBuild && !Vars.state.gameOver && event.tile.build.team != Team.derelict){
               Log.info("Core destroyed @ ", event.tile.build.team);
               if(event.tile.build.team.cores().size <= 1){
                   Call.sendMessage(String.format("[gold] %s lost...", event.tile.build.team.name));
                   //other player get a point
                   updatePoints(event.tile.team(), -1, 1);

               }
           }
        });

        Events.on(WorldLoadEvent.class, event -> {
            Timer.schedule(()->{
                timePlayerInfo tpi;
                for(Player p: Groups.player.copy(new Seq<>())){
                    tpi = playerInfo.get(p.uuid());
                    if(tpi == null){
                        //player changed his uuid
                        tpi = changedUUID(p);
                        if(tpi == null){
                            Log.info("<pvpStats> Kicked @ because he changed his UUID", p.name);
                            Call.kick(p.con, "changed UUID mid game\n[sky]Go to discord for more info...");
                            continue;
                        }
                    }

                    tpi.setTeam(p.team());
                }
            }, 1.5f);

            //sort leaderboard
            Log.info("<pvpStats> update leaderboard");
            Core.app.post(() -> sortLeaderboard());
        });

        Events.on(EventType.GameOverEvent.class, event -> {
            Log.info(String.format("Winner %s",event.winner));
            //new map
            //updatePoints(event.winner, 1, -1, true);
            //update the JSON file -- run in new thread!
            new Thread(()-> dS.writeData(playerPoints)).run();
            //Core.app.post(() -> dS.writeData(playerPoints));

            //clear player history after 3 seconds
            Timer.schedule(() -> playerInfo.values().forEach(timePlayerInfo::reset),3f);
        });
    }

    //register commands that run on the server
    @Override
    public void registerServerCommands(CommandHandler handler){
        handler.register("writestats", "update the json stats file", (args)->{
            new Thread(()-> dS.writeData(playerPoints)).run();
            //Core.app.post(() -> dS.writeData(playerPoints));
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
        handler.<Player>register("lb", "show top 10", (args, player)->{
            //first step filter the objectmap --> to secure performance only do this once after "gameover"
            //should be done directly
            StringBuilder sb = new StringBuilder();
            sb.append("[green]-*- Leaderboard -*-[]\n");
            int end = (TOPPLAYERAMOUNT < lb_array.size) ? TOPPLAYERAMOUNT : lb_array.size;
            for (int i=0; i < end; i++){
                sb.append(String.format("%d : %s[][][][][] @ %d\n", i+1, lb_array.get(i).getThird(), lb_array.get(i).getSecond()));
            }

            int index = lb_array.indexOf(a -> a.getFirst().equals(player.uuid()));
            if(index > TOPPLAYERAMOUNT){
                sb.append("\n[accent]* * *[]\n");
                sb.append(String.format("%d : %s[][][][][] @ %d\n", index+1, lb_array.get(index).third, lb_array.get(index).getSecond()));
            }
            sb.append("\n[grey] Updated after mapchange...[]");
            Call.infoMessage(player.con, sb.toString());
        });
    }

    private void loadTimings(ObjectMap<String, Long> timings){
        minScoreTime = timings.get("minScoreTime");
        rageTime = timings.get("rageTime");
        teamSwitchScore = timings.get("teamSwitchScore");
    }

    private void updatePoints(Team selectTeam, int addSelect, int addOther){
        Long testtime = System.currentTimeMillis();
        Seq<Team> validTeams = new Seq<>();
        timePlayerInfo t;
        Seq<String> tbremoved = new Seq<>();
        for(String uuid: playerInfo.keys()){
            t = playerInfo.get(uuid);
            //long enough on the server
            if(!t.canUpdate(minScoreTime)){
                printer.println("cant update");
                continue;
            }
            // if the player left
            if(!t.rageQuit(rageTime) && !t.playing){
                printer.println("left");
                tbremoved.add(uuid);
                //playerInfo.remove(uuid);
                continue;
            }

            if(t.team() == selectTeam){
                printer.println("loser points");
                playerPoints.put(uuid, playerPoints.get(uuid,0)+addSelect);
            }else{
                //check evasion!
                if(t.teamSwitchEvade(teamSwitchScore, selectTeam)){
                    Log.info("<pvpStats> player @ changed teams to get points ...", t.name());
                    playerPoints.put(uuid, playerPoints.get(uuid,0)+addSelect);
                }else{
                    if(validTeams.contains(t.team())) {
                        playerPoints.put(uuid, playerPoints.get(uuid,0)+addOther);
                        printer.println("winner points");
                    }else{
                        //check if valid
                        if(t.team().cores().size > 0){
                            printer.println("winner points");
                            validTeams.add(t.team());
                            playerPoints.put(uuid, playerPoints.get(uuid, 0)+addOther);
                        }
                    }

                }
            }
            // UPDATE PLAYER NAMES
            String oldName = t.name().substring(t.name().indexOf("#")+3);
            t.name(String.format("[sky]%d [][white]#[]%s", playerPoints.get(uuid, 0), oldName));
        }
        tbremoved.forEach(u -> playerInfo.remove(u));
        Log.info("<pvpStats> Updated scores @ ms", System.currentTimeMillis()- testtime);
    }

    private timePlayerInfo changedUUID(Player p){
        Log.info("<pvpStats> UUID changed - Possible hacker: @", p.name);
        //check for the name of a player

        String oldName = Strings.stripColors(p.name().substring(p.name().indexOf("#")+1));
        if(!uuidBackUp.containsKey(oldName)){
            return null;
        }
        //fix uuid
        String oldUUID = uuidBackUp.get(oldName);
        timePlayerInfo tpi = playerInfo.get(oldUUID);
        //get oldPoints
        int oldPoints = playerPoints.remove(oldUUID,0);
        //update hashmap
        String newUUID = p.uuid();
        playerPoints.put(newUUID, oldPoints);
        playerInfo.put(newUUID, tpi);
        uuidBackUp.put(oldName, newUUID);
        //clear the old value
        playerInfo.remove(oldUUID);
        return tpi;
    }

    public void sortLeaderboard(){
        lb_array.clear();
        //
        Seq<TTriple<String, Integer, String>> presort = new Seq<>();
        for(ObjectIntMap.Entry<String> e: playerPoints.entries()){
            presort.add(new TTriple<String, Integer, String>(e.key, e.value, Vars.netServer.admins.getInfo(e.key).lastName));
        }

        //now sort on value
        presort.sort(Comparator.comparingInt(TTriple::getSecond));
        presort.reverse();
        lb_array.set(presort);
        //Administration.PlayerInfo a = Vars.netServer.admins.getInfo(presort.get(0).getFirst());
    }

    class TTriple<T1, T2, T3>{
        T1 first;
        T2 second;
        T3 third;

        public TTriple(T1 first, T2 second, T3 third){
            this.first = first;
            this.second = second;
            this.third = third;
        }

        public T1 getFirst(){
            return this.first;
        }

        public T2 getSecond(){
            return this.second;
        }

        public T3 getThird(){
            return this.third;
        }
    }

    class debugPrinter{
        boolean print;

        public debugPrinter(boolean output){
            this.print = output;
        }

        void println(String s){
            if(this.print) {
                System.out.println(s);
            }
        }

        void println(int i){
            if(this.print){
                System.out.println(i);
            }
        }
    }
}
