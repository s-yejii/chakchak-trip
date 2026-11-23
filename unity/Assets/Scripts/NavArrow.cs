// 3D 지도에서 "지금 여기" 표시: 내비게이션 화살표 (11/23 피드백: 마스코트 트리피 대신)
//   땅에 눕힌 화살촉(▲) 두 장을 겹침: 아래는 흰 테두리, 위는 파란 몸통
//   가는 쪽을 가리키고(부모인 걷는 사람 자리가 돌아감), 카메라가 멀면 키움 → 위에서 내려다봐도 화면에서 비슷한 크기
using UnityEngine;

public class NavArrow : MonoBehaviour
{
    private const float LENGTH_M = 7f;          // 화살표 길이 (카메라가 가까울 때)
    private const float WIDTH_M = 5.5f;         // 화살표 폭
    private const float NOTCH = 0.3f;           // 꼬리 가운데가 파인 깊이 (길이의 30%)
    private const float BORDER_SCALE = 1.3f;    // 흰 테두리는 몸통의 1.3배 크기
    private const float LIFT_M = 1.5f;          // 땅에서 띄우는 높이 (경로 선 위에 보이게)
    private const float SAME_SIZE_FROM_M = 60f; // 카메라가 이보다 멀면 멀어진 만큼 키움
    private const float MAX_GROW = 3f;          // 최대 3배까지

    // 걷는 사람 자리(parent) 아래 발밑에 화살표를 붙임. footDown = parent 가운데에서 땅까지 거리(m)
    public static GameObject Create(Transform parent, float footDown)
    {
        GameObject foot = new GameObject("NavArrow"); // 발밑 기준점 (여기를 키우면 제자리에서 커짐)
        foot.transform.SetParent(parent, false);
        foot.transform.localPosition = Vector3.down * footDown + Vector3.up * LIFT_M;
        AddPart(foot.transform, "ArrowBorder", BORDER_SCALE, 0f, Color.white, 3000);
        AddPart(foot.transform, "ArrowBody", 1f, 0.05f, new Color(0.10f, 0.40f, 1.00f), 3001); // 몸통을 테두리보다 나중에 그림
        foot.AddComponent<NavArrow>().Grow();
        return foot;
    }

    // 화살촉 한 장: size배 크기, up만큼 위에, color 색으로
    private static void AddPart(Transform foot, string partName, float size, float up, Color color, int drawOrder)
    {
        GameObject part = new GameObject(partName);
        part.transform.SetParent(foot, false);
        part.transform.localPosition = Vector3.up * up;
        part.AddComponent<MeshFilter>().sharedMesh = BuildArrow(size);
        Material material = new Material(Shader.Find("Sprites/Default")); // 빛과 상관없이 색 그대로 (밤에도 잘 보임), 양면
        material.color = color;
        material.renderQueue = drawOrder; // 숫자가 큰 쪽을 나중에 그림
        MeshRenderer renderer = part.AddComponent<MeshRenderer>();
        renderer.sharedMaterial = material;
        renderer.shadowCastingMode = UnityEngine.Rendering.ShadowCastingMode.Off;
    }

    // 매 화면 마지막에: 카메라가 멀면 키움
    private void LateUpdate()
    {
        Grow();
    }

    // 60m 안은 1배, 120m면 2배, 180m 넘으면 3배 (캡처 도구도 찍기 전에 부름)
    public void Grow()
    {
        Camera camera = Camera.main;
        if (camera == null) return;
        float distance = Vector3.Distance(camera.transform.position, transform.position);
        transform.localScale = Vector3.one * Mathf.Clamp(distance / SAME_SIZE_FROM_M, 1f, MAX_GROW);
    }

    // 눕힌 화살촉: 앞 끝이 +z(가는 쪽). 꼭짓점 4개 = 앞 끝, 오른쪽 뒤, 꼬리 가운데 파인 곳, 왼쪽 뒤
    private static Mesh BuildArrow(float size)
    {
        float half = LENGTH_M * size / 2;
        float side = WIDTH_M * size / 2;
        float notchZ = -half + LENGTH_M * size * NOTCH;
        Mesh mesh = new Mesh();
        mesh.vertices = new Vector3[] { new Vector3(0, 0, half), new Vector3(side, 0, -half), new Vector3(0, 0, notchZ), new Vector3(-side, 0, -half) };
        mesh.triangles = new int[] { 0, 1, 2, 0, 2, 3 }; // 위에서 봤을 때 시계 방향 = 위쪽이 앞면
        mesh.RecalculateNormals();
        mesh.RecalculateBounds();
        return mesh;
    }
}
