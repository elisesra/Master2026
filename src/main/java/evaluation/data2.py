import numpy as np
import matplotlib.pyplot as plt


def plot_literal_result_space(
    n_datapoints,
    n_models,
    n_experiments,
    max_datapoints_to_show=120
):
    """
    Literally correct 3D lattice:
    each point represents one combination:
        (datapoint_i, model_j, experiment_k)

    For readability, only some datapoint indices are shown if there are many.
    The total number of combinations is still computed from the full values.
    """

    total_results = n_datapoints * n_models * n_experiments

    # Downsample datapoints only for visualization
    if n_datapoints > max_datapoints_to_show:
        shown_datapoints = np.linspace(
            1, n_datapoints, max_datapoints_to_show, dtype=int
        )
    else:
        shown_datapoints = np.arange(1, n_datapoints + 1)

    models = np.arange(1, n_models + 1)
    experiments = np.arange(1, n_experiments + 1)

    X, Y, Z = np.meshgrid(
        shown_datapoints,
        models,
        experiments,
        indexing="ij"
    )

    fig = plt.figure(figsize=(10, 7))
    ax = fig.add_subplot(111, projection="3d")

    ax.scatter(
        X.ravel(),
        Y.ravel(),
        Z.ravel(),
        s=6,
        alpha=0.35
    )

    ax.set_xlabel(f"Datapoint index, sampled from 1 ... {n_datapoints}")
    ax.set_ylabel(f"Model index (1 ... {n_models})")
    ax.set_zlabel(f"Prompt index (1 ... {n_experiments})")

    ax.set_title(
        "Multiplicative Expansion of Result Space\n"
        f"{n_datapoints} datapoints × {n_models} models × "
        f"{n_experiments} experiments = {total_results:,} combinations"
    )

    ax.set_xlim(1, n_datapoints)
    ax.set_ylim(1, n_models)
    ax.set_zlim(1, n_experiments)

    # Key change: use visual proportions, not true numeric proportions
    ax.set_box_aspect((3, 1.5, 1.5))

    ax.view_init(elev=22, azim=-55)

    plt.tight_layout()
    plt.show()


plot_literal_result_space(
    n_datapoints=835,
    n_models=5,
    n_experiments=10
)