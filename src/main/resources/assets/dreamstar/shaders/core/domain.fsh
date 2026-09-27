#version 150
uniform sampler2D DepthSampler;
uniform vec2 ScreenSize;
uniform mat4 InverseProjection;
uniform mat4 InverseView;
uniform vec3 CameraRelative;
uniform float Radius;
uniform float Opacity;
uniform float Time;
uniform float SkyPass;
out vec4 fragColor;

float hash(vec3 p) {
    p = fract(p * .1031);
    p += dot(p, p.yzx + 33.33);
    return fract((p.x + p.y) * p.z);
}
float noise3(vec3 p) {
    vec3 i = floor(p), f = fract(p);
    f = f*f*(3.0-2.0*f);
    return mix(mix(mix(hash(i),hash(i+vec3(1,0,0)),f.x),
                   mix(hash(i+vec3(0,1,0)),hash(i+vec3(1,1,0)),f.x),f.y),
               mix(mix(hash(i+vec3(0,0,1)),hash(i+vec3(1,0,1)),f.x),
                   mix(hash(i+vec3(0,1,1)),hash(i+vec3(1,1,1)),f.x),f.y),f.z);
}
float fbm(vec3 p) {
    float v=0.0, a=.55;
    for(int i=0;i<4;i++) { v+=a*noise3(p); p=p*2.03+vec3(7.1,3.7,9.2); a*=.5; }
    return v;
}

vec3 shellPoint(vec3 camera, vec3 ray, float radius) {
    float b=dot(camera,ray);
    return camera+ray*(-b+sqrt(max(0.0,b*b+radius*radius-dot(camera,camera))));
}
float starPlane(vec2 p, float seed) {
    vec2 cell=floor(p), f=fract(p)-.5;
    float rnd=hash(vec3(cell,seed));
    vec2 offset=vec2(hash(vec3(cell,seed+3.0)),hash(vec3(cell,seed+7.0)))-.5;
    f-=offset*.55;
    float aa=max(length(fwidth(p))*.55,.009);
    float core=1.0-smoothstep(.018,.018+aa,length(f));
    float crossGlow=exp(-abs(f.x)*125.0-abs(f.y)*17.0)+exp(-abs(f.y)*125.0-abs(f.x)*17.0);
    float twinkle=.78+.22*sin(Time*1.2+rnd*100.0);
    return (core+crossGlow*.30)*step(.955,rnd)*twinkle;
}
vec3 stars(vec3 dir, float density, float seed) {
    vec3 w=pow(abs(dir),vec3(10.0)); w/=max(dot(w,vec3(1)),.001);
    float s=starPlane(dir.yz*density,seed)*w.x
           +starPlane(dir.zx*density,seed+11.0)*w.y
           +starPlane(dir.xy*density,seed+23.0)*w.z;
    return s*vec3(.64,.83,1.0);
}

float brightStarPlane(vec2 p, float seed) {
    vec2 cell=floor(p), f=fract(p)-.5;
    float rnd=hash(vec3(cell,seed));
    f-=(vec2(hash(vec3(cell,seed+2.0)),hash(vec3(cell,seed+5.0)))-.5)*.4;
    float aa=max(length(fwidth(p))*.5,.004);
    float core=1.0-smoothstep(.014,.025+aa,length(f));
    float arms=exp(-abs(f.x)*100.0-abs(f.y)*9.0)+exp(-abs(f.y)*100.0-abs(f.x)*9.0);
    return (core+arms*.65)*step(.96,rnd)*(.85+.15*sin(Time+rnd*80.0));
}
vec3 brightStars(vec3 dir) {
    vec3 w=pow(abs(dir),vec3(10)); w/=max(dot(w,vec3(1)),.001);
    float s=brightStarPlane(dir.yz*15.0,42.0)*w.x
           +brightStarPlane(dir.zx*15.0,57.0)*w.y
           +brightStarPlane(dir.xy*15.0,71.0)*w.z;
    return s*vec3(.70,.68,1.0);
}

vec3 planet(vec3 camera, vec3 ray, vec3 background) {
    vec3 center=vec3(0.0,-30.0,0.0), oc=camera-center;
    float b=dot(oc,ray), h=b*b-dot(oc,oc)+100.0;
    if(h>0.0) {
        float t=-b-sqrt(h);
        if(t>0.0) {
            vec3 n=normalize(camera+ray*t-center);
            float cloud=fbm(n*5.0+vec3(0,Time*.006,0));
            float detail=fbm(n*24.0+cloud*3.0);
            vec3 col=mix(vec3(.055,.07,.4),vec3(.28,.5,.95),smoothstep(.25,.62,cloud));
            col=mix(col,vec3(.7,.5,1),smoothstep(.55,.76,cloud));
            col+=vec3(.12,.22,.36)*smoothstep(.4,.72,detail);
            float lighting=.48+.52*max(dot(n,normalize(vec3(-1,1,-.7))),0.0);
            float rim=pow(1.0-max(dot(n,-ray),0.0),3.0);
            background=col*lighting+rim*vec3(.35,.7,1);
        }
    }
    return background;
}

vec3 cosmos(vec3 ray, vec3 virtualCamera) {
    vec3 farPoint=shellPoint(virtualCamera,ray,85.0);
    vec3 direction=normalize(farPoint);
    vec3 cloudPoint=floor(farPoint*3.0)/3.0;
    float cloud=fbm(cloudPoint*.052+vec3(Time*.003,0,0));
    float band=dot(direction,normalize(vec3(.55,.24,.80)));
    float warp=fbm(cloudPoint*.11+vec3(4,7,1))-.5;
    float ribbon=exp(-pow((band+warp*.22)*4.2,2.0));
    float wisps=fbm(cloudPoint*.15+vec3(5,2,8));
    vec3 color=vec3(.022,.028,.13);
    color+=vec3(.08,.15,.66)*cloud;
    color+=mix(vec3(.14,.38,.95),vec3(.58,.21,.87),wisps)*ribbon*smoothstep(.28,.68,cloud)*1.4;
    color+=vec3(.4,.68,.9)*pow(cloud,4.0)*ribbon*2.4;
    float dust=fbm(cloudPoint*.31+vec3(9,1,3));
    float core=exp(-pow((band+warp*.16)*13.0,2.0));
    color+=mix(vec3(.24,.22,.9),vec3(.92,.6,1.0),wisps)*core*smoothstep(.28,.63,dust)*.9;
    color*=1.0-.42*ribbon*smoothstep(.50,.70,wisps)*smoothstep(.3,.6,cloud);
    color+=vec3(.1,.2,.4)*smoothstep(.42,.72,dust)*ribbon;
    color+=stars(direction,110.0,2.0)*.55;
    color+=stars(normalize(shellPoint(virtualCamera,ray,45.0)),65.0,17.0)*.8;
    color=planet(virtualCamera,ray,color);
    color+=stars(normalize(shellPoint(virtualCamera,ray,23.0)),32.0,31.0);
    color+=brightStars(normalize(shellPoint(virtualCamera,ray,26.0)));
    return color;
}

vec3 skyClouds(vec3 ray, vec3 color) {
    vec3 p=floor(ray*240.0)/240.0;
    float broad=fbm(p*4.8+vec3(Time*.007,1,2));
    float broken=fbm(p*12.0+vec3(7,Time*.004,9));
    float detail=noise3(p*65.0+vec3(4,2,8));
    float low=1.0-smoothstep(.08,.70,abs(ray.y));
    float horizon=exp(-abs(ray.y)*4.0);
    float cloud=smoothstep(.31,.63,broad*.72+broken*.28);
    float coverage=cloud*(.32+.58*low);
    vec3 bank=mix(vec3(.16,.06,.42),vec3(.49,.18,.85),broken);
    bank+=vec3(.30,.14,.34)*horizon;
    bank+=vec3(.21,.15,.32)*smoothstep(.40,.66,detail)*cloud;
    color=mix(color,bank,coverage);
    float ribbons=pow(.5+.5*sin(ray.y*44.0+broad*10.0),8.0)*low*cloud;
    color+=ribbons*vec3(.19,.09,.3);
    color+=horizon*horizon*vec3(.18,.06,.20);
    return color;
}

void main() {
    vec2 uv=gl_FragCoord.xy/ScreenSize;
    if(SkyPass>.5) {
        vec4 view=InverseProjection*vec4(uv*2.0-1.0,1.0,1.0);
        vec3 ray=normalize((InverseView*vec4(view.xyz/view.w,0.0)).xyz);
        vec3 color=skyClouds(ray,cosmos(ray,CameraRelative*.20));
        fragColor=vec4(clamp(color,0.0,1.0),Opacity);
        return;
    }
    float depth=texture(DepthSampler,uv).r;
    if(depth>=.999999) discard;
    vec4 view=InverseProjection*vec4(uv*2.0-1.0,depth*2.0-1.0,1.0);
    view/=view.w;
    vec3 relative=(InverseView*vec4(view.xyz,1.0)).xyz;
    vec3 world=relative+CameraRelative;
    float distanceFromCenter=length(world);
    if(distanceFromCenter>=Radius) discard;
    vec3 ray=normalize(relative);
    vec3 color=cosmos(ray,CameraRelative*.20);
    vec3 normal=normalize(cross(dFdx(world),dFdy(world)));
    float relief=.84+.16*abs(normal.y);
    color*=relief;
    float frost=pow(noise3(world*2.5),12.0)*.25;
    color+=frost*vec3(.45,.8,1);
    float mask=(1.0-smoothstep(Radius-.65,Radius,distanceFromCenter))*Opacity;
    fragColor=vec4(clamp(color,0.0,1.0),mask);
}
