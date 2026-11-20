import {Composition} from 'remotion';
import {Main} from './Main';

export const RemotionRoot: React.FC = () => {
  return (
    <>
      <Composition
        id="Promo"
        component={Main}
        durationInFrames={900}
        fps={30}
        width={1440}
        height={1080}
      />
    </>
  );
};
