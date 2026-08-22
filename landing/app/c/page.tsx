"use client";

import { useEffect, useState } from "react";
import { EmotionCard, SketchFilters, StoreButtons } from "../components";
import { EMOTIONS, type Emotion } from "../emotions";
import { APP_STORE_URL, IOS_PENDING_FORM_URL, PLAY_STORE_URL } from "../site";

/**
 * 공유 링크(`gamss.kr/c/{토큰}`)로 도착하는 페이지.
 *
 * **카드를 보여주는 곳이 아니다.** 공유는 인스타 스토리에 카드 *이미지* 와 이 링크를 함께 올리는
 * 방식이라, 링크를 누른 사람은 이미 카드를 봤다. 그래서 이 링크가 할 일은 하나뿐이다 —
 * **앱으로 데려가는 것.**
 *
 * 앱이 깔려 있으면 여기까지 오지 않는다. OS 가 검증 파일을 보고 링크를 앱으로 넘겨 메인 화면을
 * 연다(`deploy/nginx/conf/gamss.conf` 의 AASA·assetlinks 참고). 그러니 이 페이지는 사실상
 * **앱이 없는 사람이 도착하는 곳**이고, 곧바로 스토어로 보낸다.
 *
 * 다만 PC 는 스토어로 보내봐야 설치할 수 없다. 그쪽에는 카드를 보여주고 버튼을 남긴다 —
 * 인스타·카톡 인앱 브라우저가 딥링크를 무시하고 URL 을 여는 경우의 안전망이기도 하다.
 *
 * **iOS 심사 중에는 아이폰도 PC 와 같다**([IOS_PENDING_FORM_URL]). 보낼 스토어가 아직 없고,
 * 그 자리의 사전 알림 폼으로 자동으로 보내면 카드를 보러 온 사람이 아무것도 못 보고 끌려간다.
 *
 * 정적 익스포트라 토큰마다 페이지를 만들 수 없다. 그래서 이 한 장을 `/c/` 아래 모든 경로에
 * 내려주고(nginx `try_files ... /c.html`), 토큰은 주소에서 직접 읽는다.
 */
export default function SharedCardPage() {
  const [state, setState] = useState<State>({ status: "loading" });

  useEffect(() => {
    const store = storeUrlFor(navigator.userAgent);
    if (store) {
      // replace 로 보낸다. push 면 뒤로가기가 이 페이지로 돌아와 다시 스토어로 튕긴다.
      window.location.replace(store);
      return;
    }

    const token = window.location.pathname.split("/").filter(Boolean).pop() ?? "";
    if (!token) {
      setState({ status: "gone" });
      return;
    }

    let alive = true;
    fetchSharedCard(token)
      .then((card) => alive && setState(card ? { status: "ready", card } : { status: "gone" }))
      .catch(() => alive && setState({ status: "error" }));
    return () => {
      alive = false;
    };
  }, []);

  return (
    <>
      <SketchFilters />
      <main className="flex min-h-dvh flex-col items-center justify-center gap-9 px-6 py-16 text-center">
        <a href="/" aria-label="GAMSS 홈">
          <span className="logo-mask block h-[28px] w-[86px] text-ink" />
        </a>

        <Body state={state} />
      </main>
    </>
  );
}

/**
 * 이 기기가 갈 스토어. 스토어가 없는 기기(PC)면 null.
 *
 * UA 로 고르는 것은 정확하지 않지만, 틀려도 손해가 작다 — 잘못 고르면 설치할 수 없는 스토어
 * 페이지가 뜰 뿐이고, 아예 못 고르면 카드와 두 버튼이 있는 페이지가 남는다.
 */
function storeUrlFor(userAgent: string): string | null {
  if (/android/i.test(userAgent)) return PLAY_STORE_URL;
  // iPadOS 13+ 는 UA 에 스스로를 Mac 이라고 적는다. 터치 지원 여부로 갈라낸다.
  const isIOS =
    /iphone|ipad|ipod/i.test(userAgent) ||
    (/macintosh/i.test(userAgent) && navigator.maxTouchPoints > 1);
  // 심사 중에는 아이폰도 PC 처럼 둔다. 여기서 사전 알림 폼으로 보내면 카드를 보러 온 사람이
  // 아무것도 못 보고 구글 폼으로 끌려간다 — 폼으로 갈지는 카드를 본 뒤 본인이 정한다.
  if (isIOS) return IOS_PENDING_FORM_URL ? null : APP_STORE_URL;
  return null;
}

function Body({ state }: { state: State }) {
  if (state.status === "loading") {
    // 자리를 미리 잡아둬야 카드가 도착할 때 화면이 튀지 않는다.
    // 스토어로 보내는 기기에서는 이 상태 그대로 페이지를 떠난다.
    return <div className="h-[360px]" aria-live="polite" aria-busy="true" />;
  }

  if (state.status === "ready") {
    return (
      <>
        <EmotionCard
          emotion={state.card.emotion}
          date={state.card.date}
          summary={state.card.summary}
          tilt="-2deg"
        />
        <div className="flex flex-col items-center gap-5">
          <p className="text-[15px] leading-[1.75] whitespace-pre-line text-muted text-pretty">
            {"오늘 못 삼킨 말도 카드 한 장으로 남습니다.\n감쓰에서 나만의 감정 카드를 만들어보세요."}
          </p>
          <StoreButtons center />
        </div>
      </>
    );
  }

  return (
    <div className="flex flex-col items-center gap-5">
      <p className="text-[18px] font-bold">{MESSAGES[state.status].title}</p>
      <p className="text-[15px] leading-[1.75] whitespace-pre-line text-muted">
        {MESSAGES[state.status].body}
      </p>
      <StoreButtons center />
    </div>
  );
}

/**
 * 없는 링크와 글쓴이가 지운 카드는 서버가 똑같이 404 로 답한다 — 어느 쪽인지 알려주면
 * "이 토큰은 있긴 하다"는 사실이 새기 때문이다. 그래서 문구도 하나로 둔다.
 */
const MESSAGES = {
  gone: {
    title: "링크가 만료됐어요",
    body: "글쓴이가 카드를 지웠거나, 주소가 잘못됐어요.",
  },
  error: {
    title: "카드를 가져오지 못했어요",
    body: "잠시 후 다시 열어보세요.",
  },
} as const;

type SharedCard = { emotion: Emotion; summary: string; date: string };

type State =
  | { status: "loading" }
  | { status: "ready"; card: SharedCard }
  | { status: "gone" }
  | { status: "error" };

/** 카드를 못 찾으면 null, 그 밖의 실패는 throw — 화면이 둘을 다르게 안내한다. */
async function fetchSharedCard(token: string): Promise<SharedCard | null> {
  // 같은 오리진이다. nginx 가 이 경로만 앱으로 넘겨줘서 CORS 가 끼어들지 않는다.
  const response = await fetch(`/api/cards/shared/${encodeURIComponent(token)}`);
  if (response.status === 404) return null;
  if (!response.ok) throw new Error(`카드 조회 실패: ${response.status}`);

  const { data } = await response.json();
  const emotion = EMOTIONS.find((it) => it.key === String(data.emotion).toLowerCase());
  // 서버에 감정이 하나 늘고 랜딩이 아직 모르면 캐릭터를 못 고른다. 빈 카드를 그리느니 만료로 안내한다.
  if (!emotion) return null;

  return { emotion, summary: data.summary, date: formatDate(data.date) };
}

/** 서버는 `2026-07-23` 으로 준다. 카드에는 랜딩·앱과 같은 `26.07.23` 꼴로 적는다. */
function formatDate(isoDate: string): string {
  const [year, month, day] = isoDate.split("-");
  return `${year.slice(2)}.${month}.${day}`;
}
