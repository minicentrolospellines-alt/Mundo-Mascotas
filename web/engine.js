/* Shared, independently testable game rules. */
(function(root){
const WORLD_CELLS=64;
const catalog=[{id:'bow',name:'Lazo rosa',icon:'🎀',price:80,type:'accessory'},{id:'hat',name:'Sombrero',icon:'🎩',price:120,type:'accessory'},{id:'crown',name:'Corona',icon:'👑',price:220,type:'accessory'},{id:'house',name:'Casita',icon:'🏠',price:150,type:'world'},{id:'tree',name:'Árbol',icon:'🌳',price:60,type:'world'},{id:'flowers',name:'Flores',icon:'🌻',price:45,type:'world'},{id:'pool',name:'Piscina',icon:'🏊',price:200,type:'world'},{id:'slide',name:'Tobogán',icon:'🛝',price:180,type:'world'}];
const missions=[
 {id:'love',title:'Un poquito de cariño',detail:'Acaricia a tus mascotas 3 veces.',goal:3,coins:20,icon:'heart'},
 {id:'food',title:'Hora de comer',detail:'Alimenta a una mascota.',goal:1,coins:15,icon:'food'},
 {id:'clean',title:'Limpia y feliz',detail:'Baña a una mascota.',goal:1,coins:15,icon:'clean'},
 {id:'game',title:'Vamos a jugar',detail:'Termina 2 minijuegos.',goal:2,coins:30,icon:'games'},
 {id:'toy',title:'Atrapa la pelota',detail:'Atrapa la pelota 5 veces.',goal:5,coins:20,icon:'ball'},
 {id:'rest',title:'Una pequeña siesta',detail:'Deja descansar a una mascota durante 30 segundos.',goal:1,coins:20,icon:'moon'}
];
function dayKey(now=Date.now()){const d=new Date(now);return d.getFullYear()+'-'+String(d.getMonth()+1).padStart(2,'0')+'-'+String(d.getDate()).padStart(2,'0');}
function dailyMissions(s,now=Date.now()){const day=dayKey(now);if(!s.missions||s.missions.day!==day)s.missions={day,progress:{},claimed:[],bonus:false};return s.missions;}
function progress(s,id,n=1,now=Date.now()){const m=missions.find(m=>m.id===id);if(!m)return;const d=dailyMissions(s,now);d.progress[id]=Math.min(m.goal,(Number(d.progress[id])||0)+Math.max(0,n));}
function claim(s,id,now=Date.now()){const d=dailyMissions(s,now);if(id==='bonus'){if(d.bonus||!missions.every(m=>d.claimed.includes(m.id)))return 0;d.bonus=true;s.coins+=40;s.xp+=30;return 40;}const m=missions.find(m=>m.id===id);if(!m||d.claimed.includes(id)||(d.progress[id]||0)<m.goal)return 0;d.claimed.push(id);s.coins+=m.coins;s.xp+=10;return m.coins;}
function ready(s,now=Date.now()){const d=dailyMissions(s,now);return missions.filter(m=>(d.progress[m.id]||0)>=m.goal&&!d.claimed.includes(m.id)).length+(!d.bonus&&missions.every(m=>d.claimed.includes(m.id))?1:0);}
function fresh(){return {version:1,coins:250,xp:0,selected:0,pets:['Toby','Luna','Coco','Pío'].map(name=>({name,food:80,fun:80,clean:80,love:80,energy:80,sleeping:false,sleepSince:0,restCounted:false,accessory:null})),owned:[],world:Array(WORLD_CELLS).fill(null),last:Date.now(),daily:null,sound:true};}
function bounded(v,defaultValue=0,max=100){const n=Number(v);return Number.isFinite(n)?Math.max(0,Math.min(max,n)):defaultValue;}
function normalize(s){
 if(!s||s.version!==1||!Array.isArray(s.pets)||s.pets.length!==4||!Array.isArray(s.world))return fresh();
 const defaults=fresh();s.coins=Math.floor(bounded(s.coins,0,1e9));s.xp=Math.floor(bounded(s.xp,0,1e9));s.selected=Math.floor(bounded(s.selected,0,3));
 s.owned=Array.isArray(s.owned)?[...new Set(s.owned.filter(id=>catalog.some(i=>i.id===id)))]:[];
 const oldWorld=s.world;const migratedWorld=Array(WORLD_CELLS).fill(null);for(let i=0;i<Math.min(WORLD_CELLS,oldWorld.length);i++){const id=oldWorld[i];if(!catalog.some(x=>x.type==='world'&&x.id===id))continue;const target=oldWorld.length===16?(Math.floor(i/4)+2)*8+(i%4+2):i;migratedWorld[target]=id;}s.world=migratedWorld;
 s.pets=s.pets.map((v,i)=>{v=v&&typeof v==='object'?v:{};const p={...defaults.pets[i],...v};for(const k of ['food','fun','clean','love','energy'])p[k]=bounded(v[k],80);p.name=typeof v.name==='string'?v.name.slice(0,18):defaults.pets[i].name;p.sleeping=v.sleeping===true;p.sleepSince=Number.isFinite(v.sleepSince)?v.sleepSince:Date.now();p.restCounted=v.restCounted===true;return p;});
 s.last=Number.isFinite(s.last)?s.last:Date.now();const d=dailyMissions(s);d.progress=d.progress&&typeof d.progress==='object'?d.progress:{};d.claimed=Array.isArray(d.claimed)?[...new Set(d.claimed.filter(id=>missions.some(m=>m.id===id)))]:[];d.bonus=d.bonus===true;for(const m of missions)d.progress[m.id]=Math.floor(bounded(d.progress[m.id],0,m.goal));return s;
}
function age(s,now=Date.now()){
 const minutes=Math.max(0,Math.min(1440,(now-s.last)/60000));dailyMissions(s,now);
 s.pets.forEach(p=>{for(const k of ['food','fun','clean','love'])p[k]=Math.max(15,p[k]-minutes*.06);p.energy=bounded((Number.isFinite(p.energy)?p.energy:80)+minutes*(p.sleeping?4:-.04));
 if(p.sleeping&&!p.restCounted&&now-p.sleepSince>=30000){progress(s,'rest',1,now);p.restCounted=true;}});s.last=now;return s;
}
function care(s,key){const p=s.pets[s.selected];if(p.sleeping||!['food','fun','clean','love'].includes(key))return false;if(p[key]>=99)return false;if(key==='food'&&s.coins<5)return false;if(key==='food')s.coins-=5;p[key]=Math.min(100,p[key]+25);s.xp+=5;if(key!=='fun')progress(s,key);return true;}
function sleep(s,now=Date.now()){age(s,now);const p=s.pets[s.selected];p.sleeping=!p.sleeping;if(p.sleeping){p.sleepSince=now;p.restCounted=false;}return p.sleeping;}
function toy(s,now=Date.now()){age(s,now);const p=s.pets[s.selected];if(p.sleeping)return false;p.fun=Math.min(100,p.fun+5);p.energy=Math.max(0,p.energy-1);progress(s,'toy',1,now);return true;}
function buy(s,id){const item=catalog.find(i=>i.id===id);if(!item||s.owned.includes(id)||s.coins<item.price)return false;s.coins-=item.price;s.owned.push(id);return true;}
function reward(s,coins){s.coins+=Math.max(0,Math.min(100,Math.floor(coins)));s.xp+=20;s.pets[s.selected].fun=Math.min(100,s.pets[s.selected].fun+20);s.pets[s.selected].energy=Math.max(0,s.pets[s.selected].energy-5);progress(s,'game');}
function applyAdReward(s,id,coins){
 if(typeof id!=='string'||id.length<8||id.length>100||coins!==100)return 0;
 if(!Array.isArray(s.adReceipts))s.adReceipts=[];
 if(s.adReceipts.includes(id))return 0;
 s.coins+=100;s.adReceipts.push(id);s.adReceipts=s.adReceipts.slice(-256);return 100;
}
const api={WORLD_CELLS,applyAdReward,fresh,normalize,age,care,buy,reward,catalog,missions,dayKey,dailyMissions,progress,claim,ready,sleep,toy};if(typeof module!=='undefined')module.exports=api;else root.Rules=api;
})(globalThis);
