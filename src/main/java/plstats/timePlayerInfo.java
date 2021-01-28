package plstats;

import arc.struct.ObjectMap;
import arc.struct.Seq;
import arc.util.Log;
import mindustry.game.Team;
import mindustry.gen.Player;

public class timePlayerInfo {
    Player p;

    long joined;
    long left;
    boolean playing;
    public Team lastTeam;

    ObjectMap<Team, Long> teamTimer;
    //Seq<Team> teamHistory;

    public timePlayerInfo(Player p){
        this.p = p;
        this.joined = System.currentTimeMillis();
        this.lastTeam = p.team();
        this.teamTimer = new ObjectMap<>();
        this.playing = true;
    }

    public void left(){
        this.left = System.currentTimeMillis();
        this.playing = false;
    }

    public String getUUID(){
        return this.p.uuid();
    }

    public Team team(){
        return this.p.team(); //should become lastteam in the future
    }

    public String name(){
        return this.p.name();
    }

    public void name(String n){
        this.p.name(n);
    }

    // call this first
    public boolean canUpdate(long threshold){
        if(joined + threshold > System.currentTimeMillis()) {
            return false;
        }else{
            return true;
        }
    }

    // call this second
    public boolean rageQuit(long threshold){
        if(playing){
            return false;
        }
        if(left + threshold > System.currentTimeMillis()){
            return true;
        }else{
            return false;
        }
    }

    // call this last
    public boolean teamSwitchEvade(long threshold, Team lostTeam){
        System.out.println(teamTimer.toString());
        if(!teamTimer.containsKey(lostTeam)){
            return false;
        }else if(teamTimer.get(lostTeam) + threshold > System.currentTimeMillis()){
            //Log.info("@ # @", teamTimer.get(lostTeam)+threshold, System.currentTimeMillis());
            return true;
        }else{
            return false;
        }
    }

    public boolean teamChange(Team t){
        if(t != lastTeam){
            System.out.println(lastTeam.name);
            System.out.println("TEAMCHANGE");
            teamTimer.put(lastTeam, System.currentTimeMillis());
            lastTeam = t;
            return true;
        }else{
            return false;
        }
    }

    public void reset(){
        teamTimer.clear();
        lastTeam = null;
    }

    public void setTeam(Team t){
        if(playing){
            lastTeam = t;
        }
    }

}
