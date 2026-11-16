// 경로 띠 셰이더: 걸은 길(파랑)·버스(초록)·택시(주황) 띠를 그림. 길 밑에 묻히거나 건물 뒤로 가도 옅게 비쳐 보임
// 왜 만들었나: 띠가 찻길 바닥에 묻히거나 건물 안을 지나는 곳에서 끊겨 보였음 → 어디로 갔는지 한 줄로 이어서 보여야 함
// 어떻게: 같은 띠를 두 번 그림
//   1) 앞에 가린 것이 없는 곳은 제 색 그대로
//   2) 무언가에 가려진 곳은 옅게 (_HiddenAlpha 만큼만 섞어서)
// 빛·그림자 계산은 안 함 (지도 위에 그은 표시 선이라 낮이든 밤이든 같은 색이면 됨)
Shader "Retro/RouteLine"
{
    Properties
    {
        _Color ("띠 색", Color) = (0.16, 0.46, 1, 1)
        _HiddenAlpha ("가려진 곳의 진하기 (0 = 안 보임, 1 = 그대로)", Range(0, 1)) = 0.3
    }
    SubShader
    {
        // Transparent: 건물·길을 다 그린 뒤에 그림 (그래야 "이 자리가 가려졌는지"를 알 수 있음)
        Tags { "Queue" = "Transparent" "RenderType" = "Transparent" }

        CGINCLUDE
        #include "UnityCG.cginc"
        fixed4 _Color;
        float _HiddenAlpha;

        // 꼭짓점 자리를 화면 자리로 바꿈 (모든 셰이더가 하는 기본 일)
        float4 vert(float4 vertex : POSITION) : SV_POSITION
        {
            return UnityObjectToClipPos(vertex);
        }
        ENDCG

        // 1) 보이는 곳: ZTest LEqual = 이미 그려진 것보다 앞이거나 같은 거리일 때만 그림
        Pass
        {
            ZTest LEqual
            ZWrite Off
            CGPROGRAM
            #pragma vertex vert
            #pragma fragment frag
            fixed4 frag() : SV_Target
            {
                return fixed4(_Color.rgb, 1);
            }
            ENDCG
        }

        // 2) 가려진 곳: ZTest Greater = 이미 그려진 것보다 뒤일 때만 그림. Blend로 뒤 그림과 섞어서 옅게
        Pass
        {
            ZTest Greater
            ZWrite Off
            Blend SrcAlpha OneMinusSrcAlpha
            CGPROGRAM
            #pragma vertex vert
            #pragma fragment frag
            fixed4 frag() : SV_Target
            {
                return fixed4(_Color.rgb, _HiddenAlpha);
            }
            ENDCG
        }
    }
}
