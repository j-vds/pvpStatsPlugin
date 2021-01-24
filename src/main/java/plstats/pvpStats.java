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

public class pvpStats extends Plugin {
    private long minScoreTime = 90000L;
    private long rageTime = 60000L; // 60 seconds
    private long teamSwitchScore = 30000L; // if you switch 30 seconds before your team loses a core you still get deducted some points

    public ObjectIntMap<String> playerPoints; // UUID - INTEGER
    public ObjectMap<String, timePlayerInfo> playerInfo;
    public dataStorage dS;

    //private ObjectMap<Player, Team> rememberSpectate = new ObjectMap<>();

    //register event handlers and create variables in the constructor
    public pvpStats(){
        dS = new dataStorage();
        playerPoints = dS.getData();
        playerInfo = new ObjectMap<>();

        Events.on(PlayerConnect.class, event ->{
        //change the name of the player
            int points = playerPoints.get(event.player.uuid(),0);
            event.player.name(String.format("[sky]%d [white]#[] %s", points, event.player.name()));
        });

        Events.on(PlayerJoin.class, event -> {
            //save the timer!
            event.player.sendMessage("[orange] alpha version[white] - The number before your name equals your PVP score[]");
            playerInfo.put(event.player.uuid(), new timePlayerInfo(event.player));
        });

        Events.on(PlayerLeave.class, event -> {
            //save the timer!
            playerInfo.get(event.player.uuid()).left();
        });

        //detect if player changes team via a chatcommand
        Events.on(PlayerChatEvent.class, event ->{
            if(event.message.split("\\s+")[0].equals("/team")){
                playerInfo.get(event.player.uuid()).teamChange(event.player.team());
            }
        });

        Events.on(EventType.BlockDestroyEvent.class, event -> {
           if(event.tile.build instanceof CoreBlock.CoreBuild && !Vars.state.gameOver){
               Log.info("Core destroyed");
               System.out.println(event.tile.build.team);
               System.out.println(event.tile.build.team.cores().size);
               if(event.tile.build.team.cores().size <= 1){
                   Call.sendMessage(String.format("[gold] %s lost...", event.tile.build.team.name));
                   //other player get a point
                   updatePoints(event.tile.team(), -1, 1, false);

               }
           }
        });

        Events.on(EventType.GameOverEvent.class, event -> {
            Log.info(String.format("Winner %s",event.winner));
            //new map
            //updatePoints(event.winner, 1, -1, true);
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

    }

    //register commands that player can invoke in-game
    @Override
    public void registerClientCommands(CommandHandler handler){
        /*
        handler.<Player>register("forceteam", "[team] [transfer_players]","[scarlet]Admin only[] For info use the command without arguments.", (args, player) -> {
                if(!player.admin()){
                    player.sendMessage("[scarlet]Admin only");
                    return;
                }
        });

         */
    }

    private void updatePoints(Team selectTeam, int addSelect, int addOther, boolean write){
        Seq<Team> validTeams = new Seq<>();
        //Seq<Player> allPlayers = new Seq<>().with(Groups.player);
        timePlayerInfo t;
        for(String uuid: playerInfo.keys()){
            t = playerInfo.get(uuid);
            //long enough on the server
            if(!t.canUpdate(minScoreTime)){
                continue;
            }
            // if the player left on purpose
            if(!t.rageQuit(rageTime)){
                playerInfo.remove(uuid);
                continue;
            }

            if(t.team() == selectTeam){
                playerPoints.put(uuid, playerPoints.get(uuid,0)+addSelect);
            }else{
                if(validTeams.contains(t.team())) {
                    playerPoints.put(uuid, playerPoints.get(uuid,0)+addOther);
                }else{
                    //check if valid
                    if(t.team().cores().size > 0){
                        validTeams.add(t.team());
                        playerPoints.put(uuid, playerPoints.get(uuid, 0)+addOther);
                    }
                }
            }
            // UPDATE PLAYER NAMES
            String oldName = t.name().substring(t.name().indexOf("#")+1);
            t.name(String.format("[sky]%d [white]#[] %s", playerPoints.get(uuid, 0), oldName));
        }
        if(write){
            Core.app.post(() -> dS.writeData(playerPoints));
        }
    }
}
