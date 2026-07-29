package com.dsmod.probe;

import org.json.JSONArray;
import org.json.JSONObject;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** See the modern stable variant for design notes. */
final class HistoryBridge {
    private static final int MAX_SNAPSHOTS = 96;
    private static final Map<String, Snapshot> SNAPSHOTS =
            new LinkedHashMap<String, Snapshot>(MAX_SNAPSHOTS + 1, 0.75f, true) {
                @Override protected boolean removeEldestEntry(Map.Entry<String, Snapshot> eldest) {
                    return size() > MAX_SNAPSHOTS;
                }
            };
    static final class Row {
        final int messageId; final Integer parentId; final String role; final Boolean thinkingEnabled;
        final String status; final double insertedAt; final String feedbackType;
        final int accumulatedTokenUsage; final boolean banEdit; final boolean banRegenerate;
        final String tips; final String fragments; final String conversationMode;
        Row(int a, Integer b, String c, Boolean d, String e, double f, String g, int h,
            boolean i, boolean j, String k, String l, String m) {
            messageId=a; parentId=b; role=c; thinkingEnabled=d; status=e; insertedAt=f;
            feedbackType=g; accumulatedTokenUsage=h; banEdit=i; banRegenerate=j;
            tips=k; fragments=l; conversationMode=m;
        }
    }
    static final class Snapshot {
        final String sid; final Integer version; final Integer currentMessageId;
        final boolean complete; final List<Row> rows;
        Snapshot(String sid, Integer version, Integer current, boolean complete, List<Row> rows) {
            this.sid=sid; this.version=version; currentMessageId=current; this.complete=complete;
            this.rows=Collections.unmodifiableList(new ArrayList<>(rows));
        }
    }
    static final class Result {
        final int cleaned; final int rows;
        Result(int cleaned, int rows) { this.cleaned=cleaned; this.rows=rows; }
    }
    private HistoryBridge() {}
    static String stripInjectedSystemPrompts(String text) {
        if (text == null) return "";
        String out=text;
        while (true) {
            int after=injectedPrefixEnd(out);
            if (after<0) return out;
            out=out.substring(after);
        }
    }
    static String wrapSystemPrompt(String prompt, String user) {
        return "<system>\n"+(prompt==null?"":prompt)+"\n</system>\n\n"
                +stripInjectedSystemPrompts(user==null?"":user);
    }
    private static int injectedPrefixEnd(String text) {
        if (text.startsWith("<system>\n")) {
            String close="\n</system>\n\n"; int at=text.indexOf(close,9);
            return at<0?-1:at+close.length();
        }
        if (text.startsWith("<system>\r\n")) {
            String close="\r\n</system>\r\n\r\n"; int at=text.indexOf(close,10);
            return at<0?-1:at+close.length();
        }
        return -1;
    }
    static Result processHistoryResponse(Object response) {
        if (response==null) return new Result(0,0);
        Object session=field(response,"a"); String sid=str(field(session,"a"));
        Object mv=field(response,"b");
        if (sid==null||sid.length()==0||!(mv instanceof List)) return new Result(0,0);
        int cleaned=0; ArrayList<Row> rows=new ArrayList<>();
        for (Object message:(List<?>)mv) {
            if (message==null) continue;
            cleaned+=sanitizeMessage(message); Row row=row(message); if (row!=null) rows.add(row);
        }
        captureSnapshot(sid,integer(field(session,"c")),
                integer(field(session,"d")),str(field(response,"c")),rows);
        return new Result(cleaned,rows.size());
    }
    private static void captureSnapshot(String sid,Integer version,Integer current,String control,List<Row> incoming) {
        synchronized (SNAPSHOTS) {
            Snapshot old=SNAPSHOTS.get(sid);
            if (old!=null&&old.version!=null&&version!=null&&version<old.version) return;
            if (old!=null&&old.version!=null&&version==null) return;
            List<Row> rows=incoming; boolean merge="MERGE".equals(control); boolean complete=!merge;
            if (old!=null&&merge) {
                LinkedHashMap<Integer,Row> merged=new LinkedHashMap<>();
                for (Row row:old.rows) merged.put(row.messageId,row);
                for (Row row:incoming) merged.put(row.messageId,row);
                rows=new ArrayList<>(merged.values()); complete=old.complete;
            }
            SNAPSHOTS.put(sid,new Snapshot(sid,version!=null?version:(old==null?null:old.version),
                    merge&&current==null&&old!=null?old.currentMessageId:current,complete,rows));
        }
    }
    static Object snapshotLock(){return SNAPSHOTS;}
    static boolean isCurrentSnapshot(Snapshot snapshot){synchronized(SNAPSHOTS){return snapshot!=null&&SNAPSHOTS.get(snapshot.sid)==snapshot;}}
    static Snapshot snapshot(String sid) { synchronized (SNAPSHOTS) { return SNAPSHOTS.get(sid); } }
    static void forgetSession(String sid) {
        if (sid == null) return;
        synchronized (SNAPSHOTS) { SNAPSHOTS.remove(sid); }
    }
    static Result processNativeSessions(Object value) {
        if (!(value instanceof List)) return new Result(0,0);
        int cleaned=0,total=0;
        for (Object session:(List<?>)value) {
            Result result=processNativeSessionObject(session,null);
            cleaned+=result.cleaned; total+=result.rows;
        }
        return new Result(cleaned,total);
    }
    static Result processNativeSession(Object value,String targetSid) {
        if (targetSid==null||targetSid.length()==0) return new Result(0,0);
        if (value instanceof List) {
            for (Object session:(List<?>)value) {
                if (!targetSid.equals(str(field(session,"a")))) continue;
                return processNativeSessionObject(session,targetSid);
            }
            return new Result(0,0);
        }
        return processNativeSessionObject(value,targetSid);
    }
    private static Result processNativeSessionObject(Object session,String expectedSid) {
        if (session==null) return new Result(0,0);
        String sid=str(field(session,"a")); Object mv=field(session,"f");
        if (sid==null||sid.length()==0||(expectedSid!=null&&!expectedSid.equals(sid))
                ||!(mv instanceof Map)) return new Result(0,0);
        int cleaned=0; ArrayList<Row> rows=new ArrayList<>();
        try { for (Object message:new ArrayList<Object>(((Map<?,?>)mv).values())) {
            if (message==null) continue; cleaned+=sanitizeMessage(message); Row r=row(message);
            if (r!=null&&r.messageId!=Integer.MIN_VALUE) rows.add(r);
        }} catch (Throwable ignored) { return new Result(cleaned,0); }
        if (rows.isEmpty()) return new Result(cleaned,0);
        Integer current=callInteger(session,"t"); if (!contains(rows,current)) current=callInteger(session,"e");
        if (!contains(rows,current)) current=null;
        captureNative(sid,integer(field(session,"n")),current,rows);
        return new Result(cleaned,rows.size());
    }
    private static void captureNative(String sid,Integer version,Integer current,List<Row> rows) {
        synchronized (SNAPSHOTS) { Snapshot old=SNAPSHOTS.get(sid);
            if (old!=null&&old.version!=null) { if (version==null||version<old.version) return;
                if (old.complete&&version.equals(old.version)&&sameRows(old.rows,rows)
                        &&(current==null||current.equals(old.currentMessageId))) return; }
            SNAPSHOTS.put(sid,new Snapshot(sid,version,current,false,rows));
        }
    }
    private static boolean sameRows(List<Row> a,List<Row> b) { if(a.size()!=b.size())return false;
        LinkedHashMap<Integer,Row> old=new LinkedHashMap<>();for(Row r:a)old.put(r.messageId,r);
        for(Row r:b){Row x=old.get(r.messageId);if(x==null||!same(x.parentId,r.parentId)
                ||!same(x.role,r.role)||!same(x.status,r.status)||!same(x.fragments,r.fragments))return false;}return true; }
    private static boolean same(Object a,Object b){return a==b||(a!=null&&a.equals(b));}
    private static boolean contains(List<Row> rows,Integer id) { if (id==null) return false;
        for (Row row:rows) if (row.messageId==id) return true; return false; }
    private static Integer callInteger(Object target,String name) { try { Method m=target.getClass().getMethod(name);
        m.setAccessible(true); return integer(m.invoke(target)); } catch (Throwable ignored) { return null; } }
    static int sanitizeRepositoryRows(Object[] args) {
        if (args==null||args.length<5||!(args[4] instanceof List)) return 0;
        int n=0; for (Object row:(List<?>)args[4]) {
            String json=str(field(row,"l")); if (json==null) continue;
            String safe=sanitizeFragmentsJson(json);
            if (!safe.equals(json)&&set(row,"l",safe)) n++;
        } return n;
    }
    static String sanitizeFragmentsJson(String json) {
        if (json==null||json.length()==0) return json;
        try {
            JSONArray a=new JSONArray(json); boolean changed=false;
            for (int i=0;i<a.length();i++) {
                JSONObject o=a.optJSONObject(i);
                if (o==null||!"REQUEST".equals(o.optString("type"))) continue;
                String content=o.optString("content",""); String safe=stripInjectedSystemPrompts(content);
                if (!safe.equals(content)) { o.put("content",safe); changed=true; }
            }
            return changed?a.toString():json;
        } catch (Throwable ignored) { return json; }
    }
    private static int sanitizeMessage(Object message) {
        Object v=field(message,"t"); if (!(v instanceof List)) return 0;
        List list=(List)v; ArrayList<Object> replacement=null; int n=0;
        for (int i=0;i<list.size();i++) {
            Object fragment=list.get(i);
            if (!"REQUEST".equals(str(field(fragment,"a")))) continue;
            String content=str(field(fragment,"c")); if (content==null) continue;
            String safe=stripInjectedSystemPrompts(content); if (safe.equals(content)) continue;
            if (set(fragment,"c",safe)) { n++; continue; }
            Object copy=copy(fragment,safe); if (copy==null) continue;
            try { list.set(i,copy); n++; }
            catch (Throwable ignored) {
                if (replacement==null) replacement=new ArrayList<Object>(list);
                replacement.set(i,copy); n++;
            }
        }
        if (replacement!=null) set(message,"t",replacement); return n;
    }
    private static Object copy(Object fragment,String content) {
        Integer id=integer(field(fragment,"b")); if (fragment==null||id==null) return null;
        try { Constructor<?> c=fragment.getClass().getDeclaredConstructor(int.class,String.class);
            c.setAccessible(true); return c.newInstance(id.intValue(),content); }
        catch (Throwable ignored) { return null; }
    }
    private static Row row(Object message) {
        try {
            Method m=message.getClass().getMethod("O"); m.setAccessible(true); Object r=m.invoke(message);
            Integer id=integer(field(r,"a")); if (r==null||id==null) return null;
            return new Row(id,integer(field(r,"b")),str(field(r,"c")),boolObj(field(r,"d")),
                    str(field(r,"e")),dbl(field(r,"f")),str(field(r,"g")),num(field(r,"h")),
                    bool(field(r,"i")),bool(field(r,"j")),str(field(r,"k")),
                    sanitizeFragmentsJson(str(field(r,"l"))),str(field(r,"m")));
        } catch (Throwable ignored) { return null; }
    }
    private static Object field(Object target,String name) {
        if (target==null) return null;
        for (Class<?> c=target.getClass();c!=null;c=c.getSuperclass()) try {
            Field f=c.getDeclaredField(name); f.setAccessible(true); return f.get(target);
        } catch (Throwable ignored) {} return null;
    }
    private static boolean set(Object target,String name,Object value) {
        if (target==null) return false;
        for (Class<?> c=target.getClass();c!=null;c=c.getSuperclass()) try {
            Field f=c.getDeclaredField(name); f.setAccessible(true); f.set(target,value); return true;
        } catch (Throwable ignored) {} return false;
    }
    private static String str(Object v){return v instanceof String?(String)v:null;}
    private static Integer integer(Object v){return v instanceof Number?((Number)v).intValue():null;}
    private static int num(Object v){return v instanceof Number?((Number)v).intValue():0;}
    private static double dbl(Object v){return v instanceof Number?((Number)v).doubleValue():0d;}
    private static Boolean boolObj(Object v){return v instanceof Boolean?(Boolean)v:null;}
    private static boolean bool(Object v){return v instanceof Boolean&&(Boolean)v;}
}
