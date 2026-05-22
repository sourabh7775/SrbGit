// ─── Synthwave Track Builder ─────────────────────────────────────────────────

const TrackBuilder = (() => {

  // Control points — interesting circuit with long straights + chicanes
  const RAW_PTS = [
    [  0,   0],
    [100, -30],
    [200,  20],
    [240, 110],
    [220, 210],
    [160, 270],
    [ 60, 310],
    [-60, 310],
    [-160, 265],
    [-220, 180],
    [-230,  70],
    [-180, -10],
    [-90,  -30],
  ];

  const TW = 20;          // track half-width
  const SPLINE_STEPS = 28;

  /* ─── Catmull-Rom ──────────────────────────────── */
  function cr(p0,p1,p2,p3,t){
    const t2=t*t,t3=t2*t;
    return [
      .5*((2*p1[0])+(-p0[0]+p2[0])*t+(2*p0[0]-5*p1[0]+4*p2[0]-p3[0])*t2+(-p0[0]+3*p1[0]-3*p2[0]+p3[0])*t3),
      .5*((2*p1[1])+(-p0[1]+p2[1])*t+(2*p0[1]-5*p1[1]+4*p2[1]-p3[1])*t2+(-p0[1]+3*p1[1]-3*p2[1]+p3[1])*t3),
    ];
  }

  function buildSpline(pts, steps){
    const n=pts.length, out=[];
    for(let i=0;i<n;i++){
      const p0=pts[(i-1+n)%n], p1=pts[i], p2=pts[(i+1)%n], p3=pts[(i+2)%n];
      for(let s=0;s<steps;s++) out.push(cr(p0,p1,p2,p3,s/steps));
    }
    return out;
  }

  /* ─── Main build ───────────────────────────────── */
  function build(scene) {
    const spline = buildSpline(RAW_PTS, SPLINE_STEPS);
    const N = spline.length;
    const waypoints = [], boostStrips = [];
    const leftEdge = [], rightEdge = [];

    // ── asphalt surface ──
    const verts=[], idxs=[];
    for(let i=0;i<N;i++){
      const c=spline[i], nx=spline[(i+1)%N];
      const dx=nx[0]-c[0], dz=nx[1]-c[1];
      const l=Math.sqrt(dx*dx+dz*dz)||1;
      const px=-dz/l, pz=dx/l;
      leftEdge.push([c[0]+px*TW, c[1]+pz*TW]);
      rightEdge.push([c[0]-px*TW, c[1]-pz*TW]);
      waypoints.push({x:c[0],z:c[1],nx:px,nz:pz});
      const b=verts.length/3;
      verts.push(c[0]+px*TW,0,c[1]+pz*TW, c[0]-px*TW,0,c[1]-pz*TW);
      if(i<N-1){ const nb=b+2; idxs.push(b,b+1,nb+1, b,nb+1,nb); }
    }

    const geo=new THREE.BufferGeometry();
    geo.setAttribute('position',new THREE.Float32BufferAttribute(verts,3));
    geo.setIndex(idxs);
    geo.computeVertexNormals();

    // Dark asphalt with slight blue tint
    const trackMesh = new THREE.Mesh(geo, new THREE.MeshLambertMaterial({color:0x0d0d1f}));
    trackMesh.receiveShadow = true;
    scene.add(trackMesh);

    // ── kerbs (red/white striped boxes) ──
    _buildKerbs(scene, leftEdge, rightEdge, N);

    // ── neon edge tubes ──
    _buildNeonEdges(scene, leftEdge, rightEdge, N);

    // ── lane markings ──
    _buildLaneMarkings(scene, spline, N);

    // ── boost pads ──
    _buildBoostPads(scene, spline, N, waypoints, boostStrips);

    // ── start/finish gantry ──
    _buildGantry(scene, spline, waypoints[0]);

    // ── synthwave environment ──
    _buildEnvironment(scene);

    // ── lighting ──
    _buildLights(scene);

    return { waypoints, spline, boostStrips, leftEdge, rightEdge, TW };
  }

  /* ─── Kerbs ─────────────────────────────────────── */
  function _buildKerbs(scene, L, R, N){
    const matR=new THREE.MeshLambertMaterial({color:0xcc1111});
    const matW=new THREE.MeshLambertMaterial({color:0xeeeeee});
    for(let i=0;i<N;i++){
      const j=(i+1)%N;
      for(const side of [L,R]){
        const a=side[i], b=side[j];
        const dx=b[0]-a[0], dz=b[1]-a[1];
        const len=Math.sqrt(dx*dx+dz*dz);
        const mat=(Math.floor(i/2)%2===0)?matR:matW;
        const k=new THREE.Mesh(new THREE.BoxGeometry(len+.05,.18,1.2),mat);
        k.position.set((a[0]+b[0])*.5,.09,(a[1]+b[1])*.5);
        k.rotation.y=-Math.atan2(dx,dz);
        scene.add(k);
      }
    }
  }

  /* ─── Neon glowing edge tubes ───────────────────── */
  function _buildNeonEdges(scene, L, R, N){
    for(const [edge, color, emissive] of [[L,0x00f5ff,0x00f5ff],[R,0xff2d9e,0xff2d9e]]){
      const pts3d = edge.map(([x,z])=>new THREE.Vector3(x,.22,z));
      pts3d.push(pts3d[0]); // close loop
      const curve = new THREE.CatmullRomCurve3(pts3d, true);

      // Core bright tube
      const tubeGeo = new THREE.TubeGeometry(curve, N*2, 0.14, 6, true);
      const tubeMat = new THREE.MeshBasicMaterial({color:emissive});
      scene.add(new THREE.Mesh(tubeGeo, tubeMat));

      // Glow halo (larger, transparent)
      const haloGeo = new THREE.TubeGeometry(curve, N*2, 0.45, 6, true);
      const haloMat = new THREE.MeshBasicMaterial({
        color, transparent:true, opacity:0.22, depthWrite:false,
        blending:THREE.AdditiveBlending,
      });
      scene.add(new THREE.Mesh(haloGeo, haloMat));
    }
  }

  /* ─── Dashed center line ────────────────────────── */
  function _buildLaneMarkings(scene, spline, N){
    const mat=new THREE.MeshBasicMaterial({color:0xffffff,transparent:true,opacity:0.22});
    for(let i=0;i<N;i+=5){
      const c=spline[i], n=spline[(i+1)%N];
      const dx=n[0]-c[0], dz=n[1]-c[1];
      const len=Math.sqrt(dx*dx+dz*dz);
      const mesh=new THREE.Mesh(new THREE.PlaneGeometry(.25,len*4),mat);
      mesh.rotation.x=-Math.PI/2;
      mesh.position.set((c[0]+n[0])*.5,.005,(c[1]+n[1])*.5);
      mesh.rotation.z=-Math.atan2(dx,dz);
      scene.add(mesh);
    }
  }

  /* ─── Boost pads ────────────────────────────────── */
  function _buildBoostPads(scene, spline, N, waypoints, boostStrips){
    const boostMat=new THREE.MeshBasicMaterial({
      color:0x00f5ff, transparent:true, opacity:0.65,
      blending:THREE.AdditiveBlending, depthWrite:false,
    });
    const arrowMat=new THREE.MeshBasicMaterial({
      color:0xffffff, transparent:true, opacity:0.8, side:THREE.DoubleSide,
    });

    for(let i=40;i<N;i+=Math.round(N/5)){
      const c=spline[i], n=spline[(i+1)%N];
      const dx=n[0]-c[0], dz=n[1]-c[1];
      const ang=-Math.atan2(dx,dz);
      const pad=new THREE.Mesh(new THREE.PlaneGeometry(14,5),boostMat);
      pad.rotation.x=-Math.PI/2; pad.position.set(c[0],.01,c[1]);
      pad.rotation.z=ang; scene.add(pad);

      // Arrow chevrons
      for(let a=-1;a<=1;a++){
        const arr=new THREE.Mesh(new THREE.PlaneGeometry(4,1.5),arrowMat);
        arr.rotation.x=-Math.PI/2;
        arr.position.set(c[0]+Math.cos(ang)*a*1.8, .02, c[1]+Math.sin(ang)*a*1.8);
        arr.rotation.z=ang; scene.add(arr);
      }

      boostStrips.push({x:c[0], z:c[1], nx:waypoints[i].nx, nz:waypoints[i].nz});
    }
  }

  /* ─── Start/Finish Gantry ───────────────────────── */
  function _buildGantry(scene, spline, wp){
    const matP=new THREE.MeshLambertMaterial({color:0x9d00ff,emissive:0x6600cc,emissiveIntensity:0.6});
    const matB=new THREE.MeshBasicMaterial({color:0xffe600});
    const nx=wp.nx, nz=wp.nz;
    const hw=TW+3;

    // Pillars
    for(const s of [-1,1]){
      const p=new THREE.Mesh(new THREE.BoxGeometry(0.8,12,0.8),matP);
      p.position.set(wp.x+nx*s*hw,.5,wp.z+nz*s*hw);
      p.castShadow=true; scene.add(p);
    }
    // Crossbar
    const bar=new THREE.Mesh(new THREE.BoxGeometry(hw*2+4,1,0.8),matP);
    bar.position.set(wp.x,12.5,wp.z);
    bar.rotation.y=-Math.atan2(nx,nz);
    scene.add(bar);

    // Checkered start line
    const chkMat0=new THREE.MeshBasicMaterial({color:0xffffff});
    const chkMat1=new THREE.MeshBasicMaterial({color:0x000000});
    for(let row=0;row<4;row++){
      for(let col=0;col<8;col++){
        const m=new THREE.Mesh(new THREE.PlaneGeometry(2.5,1.5),(row+col)%2===0?chkMat0:chkMat1);
        m.rotation.x=-Math.PI/2;
        m.position.set(wp.x+nx*(col-3.5)*2.5,.015,wp.z+nz*(col-3.5)*2.5);
        m.rotation.z=-Math.atan2(nx,nz);
        scene.add(m);
      }
    }

    // Neon "FINISH" light strips on gantry
    const neonMat=new THREE.MeshBasicMaterial({color:0x00f5ff,blending:THREE.AdditiveBlending});
    const neon=new THREE.Mesh(new THREE.BoxGeometry(hw*2+2,.15,.15),neonMat);
    neon.position.set(wp.x,11.8,wp.z);
    neon.rotation.y=-Math.atan2(nx,nz);
    scene.add(neon);
  }

  /* ─── Synthwave Environment ─────────────────────── */
  function _buildEnvironment(scene){
    scene.background = new THREE.Color(0x04000f);
    scene.fog = new THREE.FogExp2(0x04000f, 0.0022);

    // ── Grid ground ──
    const grid = new THREE.GridHelper(2000, 160, 0x4400aa, 0x220055);
    grid.position.y = -0.05;
    grid.material.transparent = true; grid.material.opacity = 0.55;
    scene.add(grid);

    // Solid ground plane beneath grid
    const ground = new THREE.Mesh(
      new THREE.PlaneGeometry(2000,2000),
      new THREE.MeshLambertMaterial({color:0x030009})
    );
    ground.rotation.x = -Math.PI/2; ground.position.y = -0.08;
    ground.receiveShadow = true; scene.add(ground);

    // ── Retro horizon sun ──
    _buildSun(scene);

    // ── Mountain silhouettes ──
    _buildMountains(scene);

    // ── Floating neon rings (decoration) ──
    _buildRings(scene);

    // ── City skyline ──
    _buildCity(scene);
  }

  function _buildSun(scene){
    // Large half-disc on horizon
    const sunGeo = new THREE.CircleGeometry(80, 64, 0, Math.PI);
    const sunMat = new THREE.MeshBasicMaterial({
      color:0xff6090, side:THREE.DoubleSide,
      transparent:true, opacity:0.85,
    });
    const sun = new THREE.Mesh(sunGeo, sunMat);
    sun.rotation.x = Math.PI/2;
    sun.position.set(0, 5, -500);
    sun.rotation.z = Math.PI;
    scene.add(sun);

    // Horizontal stripe shadows across the sun
    const stripesMat = new THREE.MeshBasicMaterial({color:0x04000f, side:THREE.DoubleSide});
    for(let i=0;i<8;i++){
      const h = 3 + i*1.5;
      const stripe = new THREE.Mesh(new THREE.PlaneGeometry(180, h*0.35), stripesMat);
      stripe.rotation.x = Math.PI/2;
      stripe.position.set(0, 5 - i*8, -500.1);
      scene.add(stripe);
    }

    // Outer glow ring
    const glowGeo = new THREE.CircleGeometry(95, 64, 0, Math.PI);
    const glowMat = new THREE.MeshBasicMaterial({
      color:0xff4488, transparent:true, opacity:0.12,
      blending:THREE.AdditiveBlending, depthWrite:false,
    });
    const glow = new THREE.Mesh(glowGeo, glowMat);
    glow.rotation.x = Math.PI/2; glow.position.set(0,5,-501); glow.rotation.z = Math.PI;
    scene.add(glow);
  }

  function _buildMountains(scene){
    const mat = new THREE.MeshLambertMaterial({color:0x1a0033, emissive:0x0d0020});
    const edgeMat = new THREE.MeshBasicMaterial({color:0x9d00ff, transparent:true, opacity:0.5, blending:THREE.AdditiveBlending});
    const rng = seededRng(7);
    for(let i=0;i<20;i++){
      const h = 40 + rng()*80;
      const w = 50 + rng()*100;
      const x = (rng()-0.5)*700;
      const z = -280 - rng()*250;
      const geo = new THREE.ConeGeometry(w, h, 4+(i%3));
      const m = new THREE.Mesh(geo, mat);
      m.position.set(x, h*0.5-1, z);
      scene.add(m);

      // Neon outline on peaks
      const outline = new THREE.Mesh(new THREE.ConeGeometry(w+.5,h+.5,4+(i%3)), edgeMat);
      outline.position.copy(m.position);
      scene.add(outline);
    }
  }

  function _buildRings(scene){
    const mat = new THREE.MeshBasicMaterial({
      color:0x9d00ff, transparent:true, opacity:0.18,
      blending:THREE.AdditiveBlending, wireframe:true,
    });
    const rng = seededRng(13);
    for(let i=0;i<8;i++){
      const r = 30 + rng()*40;
      const ring = new THREE.Mesh(new THREE.TorusGeometry(r,0.4,4,40),mat);
      ring.position.set((rng()-0.5)*400, 20+rng()*40, -100-rng()*300);
      ring.rotation.x = rng()*Math.PI;
      scene.add(ring);
    }
  }

  function _buildCity(scene){
    const rng = seededRng(99);
    const mat1 = new THREE.MeshLambertMaterial({color:0x0a0018});
    const winMat = new THREE.MeshBasicMaterial({color:0xffee88, transparent:true, opacity:.4, blending:THREE.AdditiveBlending});
    for(let i=0;i<60;i++){
      const h = 20 + rng()*80;
      const w = 8 + rng()*20;
      const x = (rng()-0.5)*1000;
      const z = -320 - rng()*200;
      const b = new THREE.Mesh(new THREE.BoxGeometry(w,h,w*0.7), mat1);
      b.position.set(x, h*0.5, z); scene.add(b);
      // Windows
      if(rng()>.5){
        const wm = new THREE.Mesh(new THREE.BoxGeometry(w-1,h-2,.1),winMat);
        wm.position.set(x,h*0.5,z+w*0.35+.1); scene.add(wm);
      }
    }
  }

  /* ─── Lighting ──────────────────────────────────── */
  function _buildLights(scene){
    scene.add(new THREE.AmbientLight(0x2211446, 1.0));

    const sun = new THREE.DirectionalLight(0xffd5ff, 1.2);
    sun.position.set(50, 120, -200);
    sun.castShadow = true;
    sun.shadow.mapSize.width = sun.shadow.mapSize.height = 2048;
    sun.shadow.camera.near=1; sun.shadow.camera.far=800;
    sun.shadow.camera.left=sun.shadow.camera.bottom=-350;
    sun.shadow.camera.right=sun.shadow.camera.top=350;
    scene.add(sun);

    // Pink fill from below
    const fill = new THREE.HemisphereLight(0x4400ff, 0xff0088, 0.4);
    scene.add(fill);

    // Track-side lamp posts
    for(let i=0;i<8;i++){
      const angle = (i/8)*Math.PI*2;
      const r = 150;
      const pl = new THREE.PointLight(0x00f5ff, 0.4, 180);
      pl.position.set(Math.cos(angle)*r, 22, Math.sin(angle)*r*1.4+150);
      scene.add(pl);
    }
  }

  function seededRng(seed){
    return function(){
      seed=seed+0x6D2B79F5|0;
      let z=Math.imul(seed^seed>>>15,1|seed);
      z=z+Math.imul(z^z>>>7,61|z)^z;
      return ((z^z>>>14)>>>0)/4294967296;
    };
  }

  return { build };
})();
