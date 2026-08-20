import {
  CharacterCard,
  EmotionCard,
  Paper,
  PhoneMockup,
  Scene,
  SecretNote,
  SectionTitle,
  SketchFilters,
  StoreButtons,
} from "./components";
import { EMOTIONS, NOTES } from "./emotions";

const CHAR_TILTS = ["-5deg", "3deg", "-2deg", "5deg", "-4deg", "2deg"];

export default function Home() {
  return (
    <>
      <SketchFilters />

      {/* ─────────────────────────── 헤더 ─────────────────────────── */}
      <div className="sticky top-0 z-40 border-b border-ink/8 bg-paper/85 backdrop-blur-md">
        <div className="mx-auto flex w-full max-w-[1060px] items-center justify-between px-6 py-3.5">
          <a href="#top" aria-label="GAMSS 홈">
            <span className="logo-mask block h-[26px] w-[80px] text-ink" />
          </a>
          <a
            href="#download"
            className="relative px-5 py-2.5 text-[13px] font-bold text-white transition-transform hover:-translate-y-0.5"
            style={{ "--sketch-fill": "#1e1f22" } as React.CSSProperties}
          >
            <span className="sketch border-white/85" />
            <span className="relative">다운로드</span>
          </a>
        </div>
      </div>

      {/* ─────────────────────────── 히어로 ─────────────────────────── */}
      {/*
        히어로는 한 화면을 채우되 내용을 위아래로 벌리지 않는다 — 큰 화면에서 가운데가 통째로
        비어 버린다. 대신 좌우로 나눠 화면이 넓을수록 오히려 꽉 차게 만든다.
        폰도 잘라내지 않는다.
      */}
      <header
        id="top"
        className="relative flex min-h-[calc(100dvh-57px)] items-center overflow-hidden"
      >
        <div className="relative mx-auto w-full max-w-[1120px] px-6 py-14">
          {/*
            비밀 메모는 뷰포트가 아니라 이 콘텐츠 상자를 기준으로 붙인다. 뷰포트 기준 %로 두면
            넓은 화면에서 양 끝으로 밀려 시선이 흩어진다.

            **상자 바깥으로만 나간다.** 안쪽은 글과 폰이 꽉 차 있어 조금만 들어와도 글자를 덮는다.
            기울여 붙이므로 회전으로 넓어지는 폭(약 30px)까지 더해 여유를 둔다.

            `2xl`(1536px) 부터만 띄운다 — 그보다 좁으면 상자 밖 여백이 메모 폭보다 작아
            대부분이 화면 밖으로 잘린다. 어설프게 걸치느니 감추는 편이 낫다.
          */}
          <div
            aria-hidden
            className="pointer-events-none absolute inset-0 hidden 2xl:block"
          >
            <div className="absolute top-8 -left-[204px]">
              <SecretNote {...NOTES[0]} tilt="-12deg" />
            </div>
            <div className="absolute bottom-4 -left-[176px]">
              <SecretNote {...NOTES[1]} tilt="6deg" />
            </div>
            <div className="absolute -right-[196px] bottom-14">
              <SecretNote {...NOTES[2]} tilt="10deg" />
            </div>
          </div>

          <div className="flex flex-col items-center gap-16 lg:flex-row lg:gap-10">
            {/* 글 */}
            <div className="relative z-10 text-center lg:flex-1 lg:text-left">
              <h1 className="text-[34px] leading-[1.3] font-bold tracking-[-0.035em] sm:text-[46px] lg:text-[52px]">
                <span className="marker">속닥속닥,</span>
                <br />
                너만 알고 있어
              </h1>

              <p className="mt-6 text-[15px] leading-[1.85] text-muted sm:text-[18px]">
                누구한테 말 못 할 말이 있다면 감쓰에 버려보세요.
                <br className="hidden sm:block" /> 여섯 감정 친구들이 대신 반응해줍니다.
              </p>

              <div className="mt-9 flex justify-center lg:justify-start">
                <StoreButtons />
              </div>

            </div>

            {/* 폰 세 대와 메모 두 장 */}
            <div className="relative flex justify-center lg:flex-1">
              <div className="flex items-end justify-center">
                <PhoneMockup
                  src="/screens/chat.webp"
                  alt="감정 친구들이 반응한 대화 화면"
                  className="z-0 mb-7 w-[104px] translate-x-5 -rotate-[9deg] sm:mb-10 sm:w-[150px] sm:translate-x-8 lg:w-[172px]"
                />
                <PhoneMockup
                  src="/screens/home.webp"
                  alt="감쓰 홈 화면"
                  className="z-10 w-[138px] sm:w-[198px] lg:w-[228px]"
                />
                <PhoneMockup
                  src="/screens/storage.webp"
                  alt="감정별 쓰레기통이 놓인 보관함 화면"
                  className="z-0 mb-7 w-[104px] -translate-x-5 rotate-[9deg] sm:mb-10 sm:w-[150px] sm:-translate-x-8 lg:w-[172px]"
                />
              </div>
            </div>
          </div>
        </div>
      </header>

      <main className="flex-1">
        <Scene
          label="아무한테도 하지 못한 말"
          labelColor="var(--color-anger)"
          title={
            <>
              누구한테 말 못 할 말이 있다면
              <br className="hidden sm:block" /> 이야기해보세요
            </>
          }
          body={
            <>
              정리하지 않아도, 예쁘게 쓰지 않아도 됩니다.
              <br className="hidden sm:block" /> 버리듯 던져두면 그걸로 끝입니다.
            </>
          }
          screen="/screens/home.webp"
          screenAlt="감쓰 홈 화면"
        />

        {/* ─────────────────── 여섯 친구 ─────────────────── */}
        <section className="mx-auto w-full max-w-[1060px] px-6 py-16 text-center sm:py-[104px]">
          <SectionTitle>
            6가지의 감정 친구들과
            <br className="hidden sm:block" /> 이야기를 이어가보세요
          </SectionTitle>
          <p className="mt-5 text-[15px] leading-[1.85] text-muted sm:text-[17px]">
            그날 대화의 분위기에 따라 다른 친구가 찾아옵니다.
          </p>

          {/* 6장을 한 줄에 늘어놓으면 폭이 모자라 마지막 한 장만 다음 줄로 떨어진다. 3열 두 줄로 둔다. */}
          <ul className="mx-auto mt-14 grid max-w-[720px] grid-cols-2 justify-items-center gap-x-5 gap-y-10 sm:grid-cols-3 sm:gap-x-7 sm:gap-y-14">
            {EMOTIONS.map((emotion, i) => (
              <li key={emotion.key}>
                <CharacterCard emotion={emotion} tilt={CHAR_TILTS[i]} />
              </li>
            ))}
          </ul>
        </section>

        <Scene
          label="대화"
          labelColor="var(--color-quirky)"
          title={
            <>
              혼잣말이 아니라
              <br className="hidden sm:block" /> 대화가 됩니다
            </>
          }
          body={
            <>
              던져둔 말에 친구들이 저마다 다르게 반응합니다.
              <br className="hidden sm:block" /> 답장을 달면 대화가 이어지고,
              친구들끼리 서로 받아치기도 합니다.
            </>
          }
          screen="/screens/chat.webp"
          screenAlt="감정 친구들이 반응한 대화 화면"
          flip
        />

        {/* ─────────────────── 오늘의 카드 ─────────────────── */}
        <Scene
          label="오늘의 카드"
          labelColor="var(--color-joy)"
          title={
            <>
              오늘의 감정을
              <br className="hidden sm:block" /> 확인해 보세요
            </>
          }
          body={
            <>
              대화를 마치면 그날의 감정이 한 장의 카드로 남습니다.
              <br className="hidden sm:block" /> 감정마다 얼굴도 색도 말투도 다릅니다.
            </>
          }
          screen="/screens/card.webp"
          screenAlt="대화를 마치면 나오는 감정 카드"
        >
          {/*
            카드를 겹쳐 부채꼴로 편다. 나란히 두면 폰 높이의 절반밖에 안 차 왼쪽 아래가 비어 보이고,
            겹쳐 두면 "여러 장이 쌓인다"는 것도 같이 보인다.
          */}
          <div className="mt-12 hidden lg:flex lg:items-end lg:pl-4">
            <div className="relative z-0 -mr-9 translate-y-3">
              <EmotionCard emotion={EMOTIONS[2]} date="26.07.29" tilt="-9deg" compact />
            </div>
            <div className="relative z-10 -mr-9 -translate-y-2">
              <EmotionCard emotion={EMOTIONS[0]} date="26.08.03" tilt="-1deg" compact />
            </div>
            <div className="relative z-20 translate-y-4">
              <EmotionCard emotion={EMOTIONS[1]} date="26.08.05" tilt="7deg" compact />
            </div>
          </div>
        </Scene>

        <Scene
          label="감정 쓰레기통"
          labelColor="var(--color-sadness)"
          title={
            <>
              지금까지의 감정을
              <br className="hidden sm:block" /> 확인해 보세요
            </>
          }
          body={
            <>
              버린 이야기는 감정별 쓰레기통에 담깁니다.
              <br className="hidden sm:block" /> 다시 보고 싶은 쓰레기통만 열어보면 됩니다.
            </>
          }
          screen="/screens/storage.webp"
          screenAlt="감정별 쓰레기통이 놓인 보관함 화면"
          flip
        />

        <Scene
          label="비우기"
          labelColor="var(--color-anxiety)"
          title={
            <>
              안좋았던 감정을
              <br className="hidden sm:block" /> 비워보세요
            </>
          }
          body={
            <>
              간직할 것과 버릴 것은 직접 고릅니다.
              <br className="hidden sm:block" /> 파쇄기에 넣으면 그날의 기록이 정말로 사라집니다.
            </>
          }
          screen="/screens/shred.webp"
          screenAlt="기록을 파쇄해 비우는 화면"
        />

        {/* ─────────────────── 마지막 CTA ─────────────────── */}
        <section
          id="download"
          className="mx-auto w-full max-w-[1060px] px-6 pt-10 pb-20 sm:pt-24 sm:pb-28"
        >
          <Paper
            tape="var(--color-grumpy)"
            inner="flex flex-col items-center px-6 py-16 sm:py-20"
          >
            <SectionTitle className="text-center">
              오늘도 못 삼킨 말,
              <br />
              감쓰에 버려요
            </SectionTitle>
            <div className="mt-9">
              <StoreButtons center />
            </div>
          </Paper>
        </section>
      </main>

      <footer className="border-t-2 border-dashed border-ink/20 py-9">
        <div className="mx-auto flex w-full max-w-[1060px] flex-col items-center gap-4 px-6 sm:flex-row sm:justify-between">
          <span className="logo-mask block h-[24px] w-[74px] text-muted" />
          <p className="text-[13px] text-muted">© 2026 GAMSS</p>
        </div>
      </footer>
    </>
  );
}
