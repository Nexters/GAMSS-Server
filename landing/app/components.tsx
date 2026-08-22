import type { Emotion } from "./emotions";
// 스토어 주소는 site.ts 한 곳에만 둔다 — 여기에 복사해 두면 앱 ID 가 바뀔 때 한쪽만 고치게 된다.
import { APP_STORE_URL, IOS_PENDING_FORM_URL, PLAY_STORE_URL } from "./site";

/**
 * 기기 프레임(`public/phone-frame.webp`)에서 실측한 화면 구멍의 위치·크기.
 * 프레임 이미지를 갈아끼우면 이 값도 다시 재야 한다.
 */
const FRAME = {
  w: 900,
  h: 1840,
  /*
   * 실측한 화면 구멍: x 48~851, y 46~1793.
   *
   * 구멍이 둥근 사각형이라 **가장 넓은 지점에서 재야 한다** — 위아래는 모서리를 피해 x=300 에서,
   * 좌우는 세로 한가운데에서 잰다. 모서리 근처에서 재면 실제보다 작게 나와 화면이 구멍을 못 덮고
   * 위아래로 틈이 생긴다(한 번 그렇게 재서 13px 씩 모자랐다).
   */
  left: 5.3333,
  top: 2.5,
  width: 89.3333,
  height: 95.0,
} as const;

/**
 * 화면을 구멍보다 8px(프레임 원본 기준)만큼 크게 깔아 사방으로 조금씩 흘려보낸다.
 *
 * 구멍에 딱 맞추면 반올림 오차와 둥근 모서리 때문에 실낱같은 틈이 남는다. 밝은 화면에서는
 * 배경색과 섞여 안 보이지만 **어두운 화면(카드 모달 등)에서는 흰 줄로 드러난다.**
 *
 * **키울 수 있는 여유는 베젤 두께까지다.** 실측 결과 좌우 베젤이 29px 뿐이라, 그보다 크게 주면
 * 둥근 모서리 쪽부터 화면이 프레임 밖으로 삐져나온다. 남은 틈은 아래 검은 바탕이 메운다.
 */
const BLEED_X = (8 / 900) * 100;
const BLEED_Y = (8 / 1840) * 100;

const SCREEN = {
  left: FRAME.left - BLEED_X,
  top: FRAME.top - BLEED_Y,
  width: FRAME.width + BLEED_X * 2,
  height: FRAME.height + BLEED_Y * 2,
} as const;

/**
 * 손그림 테두리·테이프가 쓰는 SVG 난수 필터. 페이지에 한 번만 심는다.
 *
 * `feTurbulence` 로 만든 잡음으로 테두리를 밀어내면 자로 그은 선이 손으로 그은 선처럼 흔들린다.
 * 반지름만 비트는 흔한 방법은 결국 매끈한 곡선이라 앱의 선과 닮지 않는다.
 */
export function SketchFilters() {
  return (
    <svg aria-hidden className="pointer-events-none absolute h-0 w-0">
      <filter id="wobble">
        <feTurbulence
          type="fractalNoise"
          baseFrequency="0.022"
          numOctaves="3"
          seed="7"
          result="noise"
        />
        <feDisplacementMap in="SourceGraphic" in2="noise" scale="4" />
      </filter>
      <filter id="wobble-soft">
        <feTurbulence
          type="fractalNoise"
          baseFrequency="0.04"
          numOctaves="2"
          seed="3"
          result="noise"
        />
        <feDisplacementMap in="SourceGraphic" in2="noise" scale="3" />
      </filter>
    </svg>
  );
}

/**
 * 손그림 종이. 바탕·테두리·질감을 [.sketch] 한 겹이 통째로 맡아 함께 흔들린다 —
 * 바탕을 이 요소에 두면 직각 사각형이 흔들린 선 밖으로 삐져나온다.
 *
 * 레이아웃 클래스는 [className] 이 아니라 [inner] 로 준다 — 내용이 한 겹 더 감싸여 있어서,
 * 바깥에 `items-center` 를 걸면 그 껍데기만 가운데로 가고 안쪽 요소는 왼쪽에 붙는다.
 */
export function Paper({
  children,
  className = "",
  inner = "",
  tilt,
  fill,
  tape,
  tapeAt = "center",
}: {
  children: React.ReactNode;
  /** 바깥 상자 — 크기·기울기 */
  className?: string;
  /** 안쪽 내용 — flex·정렬·여백 */
  inner?: string;
  tilt?: string;
  /** 종이 색. 바탕은 손그림 층이 함께 그린다. */
  fill?: string;
  /** 테이프 색. 주면 종이 위 모서리에 비스듬히 붙는다. */
  tape?: string;
  tapeAt?: "center" | "left";
}) {
  return (
    <div
      className={`relative ${className}`}
      style={
        {
          ...(tilt ? { transform: `rotate(${tilt})` } : {}),
          ...(fill ? { "--sketch-fill": fill } : {}),
        } as React.CSSProperties
      }
    >
      <span className="sketch" />
      {tape && (
        <span
          aria-hidden
          className={`tape z-20 -top-[13px] ${
            tapeAt === "center" ? "left-1/2 -ml-[37px]" : "left-7"
          }`}
          style={{ background: tape }}
        />
      )}
      <div className={`relative z-10 h-full ${inner}`}>{children}</div>
    </div>
  );
}

/**
 * 스토어 버튼. 손그림 테두리를 둘러 페이지의 다른 종이들과 같은 결로 맞춘다.
 *
 * User-Agent 로 한쪽으로 자동 이동시키지 않는다 — 인앱 브라우저에서는 앱이 깔려 있어도
 * Universal Links·App Links 가 안 먹는 경우가 많아, 이미 앱이 있는 사람까지 스토어로 튕긴다.
 */
export function StoreButtons({ center = false }: { center?: boolean }) {
  return (
    <div className={`flex flex-wrap gap-5 ${center ? "justify-center" : ""}`}>
      {/*
        iOS 는 심사 중이라 앱스토어 대신 사전 알림 폼으로 보낸다([IOS_PENDING_FORM_URL]).
        **보이는 것은 그대로 `App Store` 다** — 가는 곳만 잠시 바뀐다.
      */}
      <a
        href={IOS_PENDING_FORM_URL ?? APP_STORE_URL}
        className="relative px-8 py-4 text-[15px] font-bold text-white transition-transform hover:-translate-y-1"
        style={{ "--sketch-fill": "#1e1f22" } as React.CSSProperties}
      >
        {/* 먹색 바탕 위에서는 먹색 테두리가 안 보이므로 밝은 선으로 두른다. */}
        <span className="sketch border-white/85" />
        <span className="relative">App Store</span>
      </a>
      <a
        href={PLAY_STORE_URL}
        className="relative px-8 py-4 text-[15px] font-bold transition-transform hover:-translate-y-1"
      >
        <span className="sketch" />
        <span className="relative">Google Play</span>
      </a>
    </div>
  );
}

/** 섹션 제목. 앱 온보딩 문구를 그대로 쓰므로 두 줄로 끊긴다. */
export function SectionTitle({
  children,
  className = "",
}: {
  children: React.ReactNode;
  className?: string;
}) {
  return (
    <h2
      className={`text-[26px] leading-[1.42] font-bold tracking-[-0.03em] text-balance sm:text-[36px] ${className}`}
    >
      {children}
    </h2>
  );
}

/** 비밀을 적어둔 메모지. 테이프로 붙어 있고 글씨는 감정 색이다. */
export function SecretNote({
  text,
  color,
  tape,
  tilt,
  className = "",
}: {
  text: string;
  color: string;
  tape: string;
  tilt: string;
  className?: string;
}) {
  return (
    <Paper
      tilt={tilt}
      tape={tape}
      className={`h-[124px] w-[144px] sm:h-[142px] sm:w-[166px] ${className}`}
      inner="flex items-center justify-center px-3"
    >
      <p
        className="text-center text-[15px] leading-[1.55] font-bold whitespace-pre-line sm:text-[17px]"
        style={{ color }}
      >
        {text}
      </p>
    </Paper>
  );
}

/** 캐릭터 한 장. 앱에서 대화 상대로 나오는 여섯 친구를 폴라로이드처럼 붙여 둔다. */
export function CharacterCard({
  emotion,
  tilt,
}: {
  emotion: Emotion;
  tilt: string;
}) {
  return (
    <Paper
      tilt={tilt}
      tape={emotion.color}
      className="w-[150px] transition-transform duration-200 hover:-translate-y-1.5 sm:w-[168px]"
      inner="flex flex-col items-center px-3 pt-6 pb-4"
    >
      {/* eslint-disable-next-line @next/next/no-img-element */}
      <img
        src={`/characters/${emotion.key}.svg`}
        alt={`${emotion.displayName} 캐릭터`}
        className="h-auto w-[86%]"
      />
      <p
        className="mt-3 text-[14px] font-bold sm:text-[15px]"
        style={{ color: emotion.color }}
      >
        {emotion.displayName}
      </p>
      <p className="mt-1.5 text-center text-[12px] leading-[1.6] whitespace-nowrap text-muted">
        {emotion.trait}
      </p>
    </Paper>
  );
}

/**
 * 아이폰 목업. 프레임을 CSS 로 그린다.
 *
 * 넘겨받는 이미지에서 밋밋한 회색 테두리를 잘라내고 화면만 남겼다. 프레임을 이미지에 구우면
 * 어떤 크기로 놓아도 같은 두께가 따라와 커질수록 투박해지는데, CSS 로 그리면 폭에 비례해
 * 같이 줄고 늘어난다. 모서리 반경·베젤 두께를 폭 대비 %로 잡은 이유가 그것이다.
 */
export function PhoneMockup({
  src,
  alt,
  className = "",
}: {
  src: string;
  alt: string;
  /** 폭은 반드시 호출부가 정한다 — 기본값을 두면 임의값끼리 충돌해 호출부가 진다. */
  className: string;
}) {
  return (
    <div
      className={`relative shrink-0 ${className}`}
      style={{ aspectRatio: `${FRAME.w} / ${FRAME.h}` }}
    >
      {/*
        화면을 먼저 깔고 그 위에 프레임을 덮는다. 프레임 PNG 는 화면 자리가 뚫려 있다.

        **자르는 상자로 한 겹 감싼다.** 스크린샷과 구멍의 가로세로 비가 미세하게 달라
        `object-cover` 가 한쪽으로 넘치는데, 자를 것이 없으면 그대로 프레임 밖으로 삐져나온다.
      */}
      <span
        // 틈이 남더라도 베젤과 같은 검정이라 눈에 띄지 않는다.
        className="absolute overflow-hidden bg-black"
        style={{
          left: `${SCREEN.left}%`,
          top: `${SCREEN.top}%`,
          width: `${SCREEN.width}%`,
          height: `${SCREEN.height}%`,
          // 구멍의 모서리가 둥글어 사각형 그대로 두면 네 귀퉁이가 삐져나온다.
          borderRadius: "11.5% / 5.4%",
        }}
      >
        {/* eslint-disable-next-line @next/next/no-img-element */}
        <img src={src} alt={alt} className="h-full w-full object-cover" />
      </span>

      {/* eslint-disable-next-line @next/next/no-img-element */}
      <img
        src="/phone-frame.webp"
        alt=""
        aria-hidden
        className="absolute inset-0 h-full w-full drop-shadow-[0_14px_30px_rgba(30,31,34,0.28)]"
      />
    </div>
  );
}

/** 테이프 붙은 라벨. 섹션이 무엇에 대한 이야기인지 한 단어로 알려준다. */
export function TapeLabel({ text, color }: { text: string; color: string }) {
  return (
    <span className="relative inline-block px-3 py-1.5 text-[12px] font-bold tracking-[0.12em]">
      <span
        aria-hidden
        className="absolute inset-0 opacity-55 mix-blend-multiply [filter:url(#wobble-soft)]"
        style={{ background: color }}
      />
      <span className="relative">{text}</span>
    </span>
  );
}

/**
 * 화면 하나를 소개하는 장면. 글과 폰을 좌우로 놓고 줄마다 방향을 바꾼다.
 *
 * 폰을 카드 안에 가두지 않는다 — 앱 화면은 세로로 길어서 상자에 넣으면 상자가 세로로 길어지고,
 * 그 옆의 글은 위쪽에만 몰려 빈 자리가 크게 남는다. 종이 위에 그냥 놓아 그림자로만 띄운다.
 */
export function Scene({
  label,
  labelColor,
  title,
  body,
  screen,
  screenAlt,
  flip = false,
  children,
}: {
  label: string;
  labelColor: string;
  title: React.ReactNode;
  body?: React.ReactNode;
  screen: string;
  screenAlt: string;
  flip?: boolean;
  /** 폰 옆에 곁들일 것(카드 미리보기 등) */
  children?: React.ReactNode;
}) {
  return (
    <section className="mx-auto w-full max-w-[1060px] px-6 py-16 sm:py-[104px]">
        <div
          className={`flex flex-col items-center gap-12 lg:gap-20 ${
            flip ? "lg:flex-row-reverse" : "lg:flex-row"
          }`}
        >
          {/*
            좁은 화면에서 **폭을 반드시 채운다.** 세로로 쌓일 때 이 상자는 `items-center` 때문에
            내용만큼만 넓어지는데, 그 폭은 섹션마다 가장 긴 줄의 길이라 장면마다 글이 시작하는
            자리가 달라진다(390px 에서 24·37·52·67px 로 제각각이었다).

            폭을 채우면 정렬이 곧 시작점이 된다 — 왼쪽 정렬이라 모든 장면의 글이 같은 24px 에서
            시작한다. 폰은 그대로 가운데 둔다.
          */}
          <div className="w-full lg:flex-1">
            <TapeLabel text={label} color={labelColor} />
            <SectionTitle className="mt-5">{title}</SectionTitle>
            {body && (
              <p className="mt-5 text-[15px] leading-[1.85] text-pretty text-muted sm:text-[17px]">
                {body}
              </p>
            )}
            {children}
          </div>

          <div className="flex shrink-0 justify-center lg:flex-1">
            <PhoneMockup
              src={screen}
              alt={screenAlt}
              className="w-[208px] sm:w-[236px]"
            />
          </div>
      </div>
    </section>
  );
}

/**
 * 감정 카드. 대화를 마치면 나오는 결과물이라 이미지가 아니라 요소로 그린다 —
 * 여섯 장을 다 보여줘도 용량이 늘지 않고 큰 화면에서 깨지지 않는다.
 *
 * 맨 아래 로고는 먹색이 아니라 그 카드의 감정 색으로 칠한다(마스크로 색을 준다).
 */
export function EmotionCard({
  emotion,
  date,
  tilt,
  compact = false,
  summary = emotion.summary,
}: {
  emotion: Emotion;
  date: string;
  tilt: string;
  /** 장면 안에 곁들일 때 쓰는 작은 크기. 폰 옆에서 주인공을 가리지 않는다. */
  compact?: boolean;
  /**
   * 카드에 적을 한 줄. 기본은 랜딩용 예시([Emotion.summary])이고, 공유 링크로 연 카드는
   * 서버가 준 실제 요약을 넘긴다.
   */
  summary?: string;
}) {
  return (
    <Paper
      tilt={tilt}
      className={compact ? "w-[142px]" : "w-[228px] sm:w-[244px]"}
      inner={`flex flex-col items-center text-center ${
        compact ? "px-3 py-3.5" : "px-5 py-7"
      }`}
    >
      <p
        className={`font-bold text-ink-soft ${compact ? "text-[10px]" : "text-[14px]"}`}
      >
        {date}
      </p>

      {/* eslint-disable-next-line @next/next/no-img-element */}
      <img
        src={`/characters/${emotion.key}.svg`}
        alt={`${emotion.displayName} 캐릭터`}
        className={`w-auto ${compact ? "my-2 h-[46px]" : "my-4 h-[82px]"}`}
      />

      <div className="w-full border-t-2 border-dashed border-ink/60" />

      <p
        className={`font-bold tracking-[-0.02em] ${
          compact ? "mt-2 text-[13px]" : "mt-4 text-[20px]"
        }`}
      >
        {emotion.phrase}
      </p>
      <p
        className={`leading-[1.7] whitespace-pre-line text-muted ${
          compact ? "mt-1.5 min-h-[34px] text-[9px]" : "mt-2 min-h-[46px] text-[13px]"
        }`}
      >
        {summary}
      </p>

      <div className="w-full border-t-2 border-dashed border-ink/60" />

      <div
        className={`logo-mask ${compact ? "mt-2 h-[13px] w-[40px]" : "mt-4 h-[22px] w-[68px]"}`}
        style={{ color: emotion.color }}
        role="img"
        aria-label="GAMSS"
      />
    </Paper>
  );
}
