// 착착트립 마스코트 "트리피" (11/20): 3D 지도에서 "지금 여기" 표시 (전에는 회색 알약 모양)
//   ① 3D 모델(Resources/Mascot/trippy3d.obj)이 있으면 그걸 세움: AI로 그림 → 3D를 만들고, 깃발 글자·떨어진 조각을 고친 것
//   ② 없으면 그림 판(Resources/Mascot/trippy.png)을 세우고 늘 카메라를 마주 보게 함 (빌보드)
//   둘 다 카메라가 멀면 키움 → 위에서 내려다보는 재생 카메라에서도 화면에서 비슷한 크기 (지도 핀처럼)
using UnityEngine;

public class Trippy : MonoBehaviour
{
    private const string MODEL = "Mascot/trippy3d";         // 3D 모델 (OBJ)
    private const string MODEL_TEXTURE = "Mascot/trippy3d_tex"; // 3D 모델 색칠 그림
    private const string PICTURE = "Mascot/trippy";         // 그림 판용 그림 (바탕 투명)
    private const float HEIGHT_M = 6f;              // 키 (카메라가 가까울 때). 실제 사람보다 크게
    private const float MODEL_FACE_YAW = 0f;        // 3D 모델이 가는 쪽을 보게 돌리는 각도 (모델 파일의 앞 방향에 따라)
    private const float SAME_SIZE_FROM_M = 60f;     // 카메라가 이보다 멀면 멀어진 만큼 키움
    private const float MAX_GROW = 3f;              // 최대 3배까지 (재생 카메라 약 160m에서 2.7배)

    private bool isBillboard; // 그림 판이면 true (카메라를 마주 봄), 3D 모델이면 false (가는 쪽을 봄)

    // 걷는 사람 자리(parent) 아래 발밑에 트리피를 붙임. footDown = parent 가운데에서 땅까지 거리(m)
    public static GameObject Create(Transform parent, float footDown)
    {
        GameObject foot = new GameObject("Trippy"); // 발밑 기준점 (여기를 키우면 발은 땅에 붙은 채로 커짐)
        foot.transform.SetParent(parent, false);
        foot.transform.localPosition = Vector3.down * footDown;
        GameObject model = Resources.Load<GameObject>(MODEL);
        Trippy trippy = foot.AddComponent<Trippy>();
        trippy.isBillboard = model == null;
        if (model != null) AddModel(foot.transform, model);
        else AddBoard(foot.transform);
        trippy.FaceCamera();
        return foot;
    }

    // ① 3D 모델: 키를 HEIGHT_M에 맞추고, 발바닥이 기준점(땅)에 오게 옮김
    private static void AddModel(Transform foot, GameObject model)
    {
        GameObject body = Instantiate(model, foot, false);
        body.name = "Trippy3D";
        body.transform.localRotation = Quaternion.Euler(0, MODEL_FACE_YAW, 0);
        Material material = new Material(Shader.Find("Standard")); // 빛·그림자를 받는 기본 재질 (길 사진 재질과 같은 셰이더)
        material.mainTexture = Resources.Load<Texture2D>(MODEL_TEXTURE);
        material.SetFloat("_Glossiness", 0.15f); // 살짝만 반짝 (장난감 느낌)
        Bounds bounds = new Bounds();
        bool hasBounds = false;
        foreach (MeshFilter filter in body.GetComponentsInChildren<MeshFilter>())
        {
            filter.GetComponent<Renderer>().sharedMaterial = material;
            filter.GetComponent<Renderer>().shadowCastingMode = UnityEngine.Rendering.ShadowCastingMode.On;
            Bounds own = filter.sharedMesh.bounds; // 모델 파일 단위(대략 키 0.85)
            if (!hasBounds) { bounds = own; hasBounds = true; }
            else bounds.Encapsulate(own);
        }
        if (!hasBounds) return;
        float scale = HEIGHT_M / bounds.size.y;
        body.transform.localScale = Vector3.one * scale;
        body.transform.localPosition = Vector3.up * (-bounds.min.y * scale); // 발바닥(가장 낮은 곳)을 기준점 높이로
    }

    // ② 그림 판: 아래 가운데가 기준점인 네모 판에 그림을 붙임
    private static void AddBoard(Transform foot)
    {
        Texture2D picture = Resources.Load<Texture2D>(PICTURE);
        GameObject board = new GameObject("TrippyBoard");
        board.transform.SetParent(foot, false);
        float width = picture != null ? HEIGHT_M * picture.width / picture.height : HEIGHT_M; // 그림 가로세로 비율 그대로
        board.AddComponent<MeshFilter>().sharedMesh = BuildBoard(width, HEIGHT_M);
        Material material = new Material(Shader.Find("Sprites/Default")); // 투명한 곳은 비치고, 밤에도 그림 색 그대로
        material.mainTexture = picture;
        board.AddComponent<MeshRenderer>().sharedMaterial = material;
    }

    // 매 화면 마지막에: 카메라 쪽으로 (그림 판) · 거리만큼 키우기 (둘 다)
    private void LateUpdate()
    {
        FaceCamera();
    }

    // 그림 판이면 카메라와 같은 쪽을 보게 돌리고, 카메라가 멀면 키움 (캡처 도구도 찍기 전에 부름)
    public void FaceCamera()
    {
        Camera camera = Camera.main;
        if (camera == null) return;
        if (isBillboard) transform.rotation = Quaternion.LookRotation(camera.transform.forward, camera.transform.up);
        // 60m 안은 1배, 120m면 2배, 180m 넘으면 3배 (위에서 보는 재생 카메라에서 점처럼 작았음)
        float distance = Vector3.Distance(camera.transform.position, transform.position);
        transform.localScale = Vector3.one * Mathf.Clamp(distance / SAME_SIZE_FROM_M, 1f, MAX_GROW);
    }

    // 아래 가운데가 (0, 0, 0)인 네모 판. 앞면은 −z 쪽 (LookRotation으로 돌리면 카메라 쪽)
    private static Mesh BuildBoard(float width, float height)
    {
        Mesh mesh = new Mesh();
        mesh.vertices = new Vector3[] { new Vector3(-width / 2, 0, 0), new Vector3(width / 2, 0, 0), new Vector3(-width / 2, height, 0), new Vector3(width / 2, height, 0) };
        mesh.uv = new Vector2[] { new Vector2(0, 0), new Vector2(1, 0), new Vector2(0, 1), new Vector2(1, 1) };
        mesh.triangles = new int[] { 0, 2, 1, 2, 3, 1 };
        mesh.RecalculateNormals();
        mesh.RecalculateBounds();
        return mesh;
    }
}
