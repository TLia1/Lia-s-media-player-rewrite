package com.lia.mediaplayer.ui.anim;

/** Maps linear progress, 0 to 1, onto the curve an animation follows. */
@FunctionalInterface
public interface Easing {

    Easing LINEAR = t -> t;

    /** Fast start, gentle landing: things arriving. */
    Easing OUT_CUBIC = t -> {
        float u = 1 - t;
        return 1 - u * u * u;
    };

    /** Gentle start, fast finish: things leaving. */
    Easing IN_CUBIC = t -> t * t * t;

    float apply(float t);
}
