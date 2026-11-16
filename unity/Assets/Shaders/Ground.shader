// 땅 셰이더: 바닥 사진 위에, 가까운 곳에만 잔디·보도블록 무늬를 겹쳐 그림
// 왜 만들었나: 바닥 사진은 한 픽셀이 약 3m라 가까이서 보면 찰흙처럼 민무늬로 보임
//   유니티 기본 재질(Standard)은 겹치는 무늬가 한 가지뿐이라, 풀밭과 포장 바닥에 다른 무늬를 못 줌 → 바닥 색을 보고 고르게 따로 만듦
Shader "Retro/Ground"
{
    Properties
    {
        _MainTex ("바닥 사진", 2D) = "white" {}
        _Color ("바닥 색 (사진이 없을 때)", Color) = (1, 1, 1, 1)
        _GrassTex ("잔디 무늬", 2D) = "gray" {}
        _PavingTex ("보도블록 무늬", 2D) = "gray" {}
        _GrassM ("잔디 무늬 한 장 크기(m)", Float) = 3
        _PavingM ("보도블록 무늬 한 장 크기(m)", Float) = 2.4
        _NearM ("무늬가 보이는 거리(m)", Float) = 150
        _FarPatchM ("멀리서 보이는 큰 얼룩 한 장 크기(m)", Float) = 48
    }
    SubShader
    {
        Tags { "RenderType" = "Opaque" }

        CGPROGRAM
        // surf 함수로 색을 정하고, 빛·그림자 계산은 유니티 기본(Standard)에 맡김
        #pragma surface surf Standard fullforwardshadows
        #pragma target 3.0

        sampler2D _MainTex;
        sampler2D _GrassTex;
        sampler2D _PavingTex;
        fixed4 _Color;
        float _GrassM;
        float _PavingM;
        float _NearM;
        float _FarPatchM;

        struct Input
        {
            float2 uv_MainTex; // 바닥 사진에서 이 점의 자리
            float3 worldPos;   // 이 점의 실제 위치(m)
        };

        void surf(Input IN, inout SurfaceOutputStandard o)
        {
            fixed3 base = tex2D(_MainTex, IN.uv_MainTex).rgb * _Color.rgb;

            // 풀·흙 정도 (0 = 포장 바닥, 1 = 풀밭·숲 바닥): 초록이 파랑보다 얼마나 센지
            //   회색 포장은 세 색이 비슷하고, 풀·낙엽·흙은 파랑이 적음. 빨강은 안 봄 (누런 숲 바닥도 짙은 초록 공원도 풀로 봐야 해서)
            //   예) 잔디 (0.38, 0.47, 0.25) → 0.47 - 0.25 = 0.22 → (0.22 - 0.05) x 12 → 1
            //       맨땅 회색 (0.48, 0.46, 0.44) → 0.46 - 0.44 = 0.02 → 음수 → 0
            float green = saturate((base.g - base.b - 0.05) * 12);

            // 무늬는 실제 위치(m)로 깔아서 지도 크기와 상관없이 늘 같은 크기
            fixed3 grass = tex2D(_GrassTex, IN.worldPos.xz / _GrassM).rgb;
            fixed3 paving = tex2D(_PavingTex, IN.worldPos.xz / _PavingM).rgb;
            // 무늬는 평균이 회색(0.5) → ×2 하면 평균 1이라 바닥 밝기는 그대로, 무늬만 생김
            fixed3 pattern = lerp(paving, grass, green) * 2;

            // 카메라에서 멀수록 무늬를 옅게 (멀리서는 어차피 안 보이고, 자글거리기만 함)
            float near = saturate(1 - distance(IN.worldPos, _WorldSpaceCameraPos) / _NearM);
            fixed3 color = base * lerp(fixed3(1, 1, 1), pattern, near);

            // 멀리서 보이는 큰 얼룩: 같은 잔디 그림을 크게 한 번 더 겹침 → 위에서 내려다봐도 찰흙처럼 민무늬로 안 보임
            //   세기: 풀밭 0.5, 포장 바닥 0.25 (세면 바둑판처럼 보임)
            //   가까운 곳은 뺌 (1 − near): 큰 그림은 가까이서 보면 네모난 점이 그대로 보여서
            fixed3 patch = tex2D(_GrassTex, IN.worldPos.xz / _FarPatchM).rgb * 2;
            o.Albedo = color * lerp(fixed3(1, 1, 1), patch, lerp(0.25, 0.5, green) * (1 - near));
            o.Metallic = 0;
            o.Smoothness = 0.05; // 번들거리지 않게
        }
        ENDCG
    }
    FallBack "Diffuse"
}
