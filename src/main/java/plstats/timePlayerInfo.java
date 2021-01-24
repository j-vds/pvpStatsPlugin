package plstats;

import arc.struct.ObjectMap;
import arc.struct.Seq;
import mindustry.game.Team;
import mindustry.gen.Player;

public class timePlayerInfo {
    Player p;

    long joined;
    long left;
    Team lastTeam;

    ObjectMap<Team, Long> teamTimer;
    Seq<Team> teamEvolution;

    public timePlayerInfo(Player p){
        this.p = p;
        this.joined = System.currentTimeMillis();
        this.lastTeam = p.team();
    }

    public void left(){
        this.left = System.currentTimeMillis();
    }

    // call this first
    public boolean canUpdate(long threshold){
        if(joined + threshold > System.currentTimeMillis()){
            return false;
        }else{
            return true;
        }
    }

    // call this second
    public boolean rageQuit(long threshold){
        if(left + threshold > System.currentTimeMillis()){
            return true;
        }else{
            return false;
        }
    }



    public boolean teamChange(Team t){
        if(t != lastTeam){

            lastTeam = t;
            return true;
        }else{
            return false;
        }
    }
}
