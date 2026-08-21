/**
 * 감정 6종. 서버 `EmotionType` 상수와 키를 맞춰 두면 캐릭터 파일명(`/characters/{key}.svg`)이
 * 그대로 따라온다.
 *
 * 색은 디자인 팔레트가 아니라 **에셋에 박힌 값**을 옮긴 것이다(`design/colors.md` 참고).
 */
export type Emotion = {
  key: string;
  /** 보관함 화면의 쓰레기통에 적히는 이름. 여기에는 `이` 가 붙지 않는다 */
  label: string;
  /**
   * 대화 화면에서 부르는 캐릭터 이름.
   *
   * **[label] 에 `이` 를 붙여 만들면 안 된다** — 분노만 `이` 가 붙지 않는다.
   * 규칙이 아니라 예외라서 여섯 개를 그대로 적어둔다(에셋 파일명도 `분노_프로필` 하나만 다르다).
   */
  displayName: string;
  /** 카드에 적히는 문구 */
  phrase: string;
  color: string;
  /** 카드 미리보기에 넣을 대화 요약 예시. **3줄을 넘기지 않는다** — 카드 높이가 서로 어긋난다. */
  summary: string;
  /** 이 친구가 대화에서 맡는 역할. **한 줄에 들어가야 한다**(13자 안팎) — 넘치면 그 카드만 높아진다. */
  trait: string;
};

export const EMOTIONS: Emotion[] = [
  {
    key: "anger",
    label: "분노",
    displayName: "분노",
    phrase: "오늘 화~나네",
    color: "#e34225",
    summary: "다 끝낸 일을 다시 하라는 말을 들었다.",
    trait: "내 일처럼 같이 화내줘요"
  },
  {
    key: "joy",
    label: "기쁨",
    displayName: "기쁨이",
    phrase: "오늘 기~쁘네",
    color: "#f1b235",
    summary: "오래 준비한 일이 드디어 잘 풀렸다.",
    trait: "작은 일도 크게 기뻐해줘요"
  },
  {
    key: "sadness",
    label: "슬픔",
    displayName: "슬픔이",
    phrase: "오늘 슬~프네",
    color: "#376ce8",
    summary: "괜찮다고 해놓고 계속 그 생각만 났다.",
    trait: "옆에서 조용히 같이 울어줘요"
  },
  {
    key: "grumpy",
    label: "까칠",
    displayName: "까칠이",
    phrase: "오늘 까~칠하네",
    color: "#a425e3",
    summary: "오늘은 누가 말만 걸어도 짜증이 났다.",
    trait: "시니컬하게 한마디 툭 던져요"
  },
  {
    key: "anxiety",
    label: "불안",
    displayName: "불안이",
    phrase: "오늘 불~안하네",
    color: "#36af48",
    summary: "아무 일도 없었는데 잠이 오지 않는다.",
    trait: "같이 안절부절 걱정해줘요"
  },
  {
    key: "quirky",
    label: "엉뚱",
    displayName: "엉뚱이",
    phrase: "오늘 엉~뚱하네",
    color: "#36b8bd",
    summary: "쓸데없는 생각만 했는데 그게 제일 재밌었다.",
    trait: "엉뚱한 소리로 웃겨줘요"
  },
];

/** 온보딩 1번 화면의 메모지들. 서비스가 어떤 말을 받아주는지 보여주는 예시다. */
export const NOTES = [
  { text: "임금님 귀는\n당나귀 귀", color: "#e34225", tape: "rgba(227, 66, 37, 0.25)" },
  { text: "팀장은 외계인", color: "#36af48", tape: "rgba(54, 175, 72, 0.25)" },
  { text: "내 귀에\n도청장치", color: "#376ce8", tape: "rgba(55, 108, 232, 0.25)" },
];
