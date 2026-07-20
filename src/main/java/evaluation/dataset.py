import matplotlib.pyplot as plt
import numpy as np


def plot_result_space(x_datapoints, y_models, z_experiments):
    """
    Plot a 3D conceptual figure showing multiplicative result-space growth.

    Parameters
    ----------
    x_datapoints : int
        Number of input datapoints.
    y_models : int
        Number of models.
    z_experiments : int
        Number of experiments or comparison models.

    Example
    -------
    plot_result_space(500, 10, 10)
    """

    total_results = x_datapoints * y_models * z_experiments

    fig = plt.figure(figsize=(9, 7))
    ax = fig.add_subplot(111, projection="3d")

    # Create a transparent cuboid representing the full result space
    x = [0, x_datapoints]
    y = [0, y_models]
    z = [0, z_experiments]

    vertices = np.array([
        [x[0], y[0], z[0]],
        [x[1], y[0], z[0]],
        [x[1], y[1], z[0]],
        [x[0], y[1], z[0]],
        [x[0], y[0], z[1]],
        [x[1], y[0], z[1]],
        [x[1], y[1], z[1]],
        [x[0], y[1], z[1]],
    ])

    edges = [
        (0, 1), (1, 2), (2, 3), (3, 0),
        (4, 5), (5, 6), (6, 7), (7, 4),
        (0, 4), (1, 5), (2, 6), (3, 7)
    ]

    for start, end in edges:
        ax.plot(
            [vertices[start, 0], vertices[end, 0]],
            [vertices[start, 1], vertices[end, 1]],
            [vertices[start, 2], vertices[end, 2]],
            linewidth=2
        )

    # Add a few scattered points to suggest combinations inside the result space
    rng = np.random.default_rng(42)

    n_points = min(300, total_results)

    sample_x = rng.uniform(0, x_datapoints, n_points)
    sample_y = rng.uniform(0, y_models, n_points)
    sample_z = rng.uniform(0, z_experiments, n_points)

    ax.scatter(sample_x, sample_y, sample_z, alpha=0.35, s=12)

    # Labels
    ax.set_xlabel(f"Input Data: {x_datapoints}")
    ax.set_ylabel(f"Models: {y_models}")
    ax.set_zlabel(f"Prompts: {z_experiments}")

    ax.set_title(
        f"Multiplicative Growth of Result Space\n"
        f"{x_datapoints} × {y_models} × {z_experiments} = {total_results:,} results"
    )

    # Set axis limits
    ax.set_xlim(0, x_datapoints)
    ax.set_ylim(0, y_models)
    ax.set_zlim(0, z_experiments)

    # Make the perspective easier to read
    ax.view_init(elev=25, azim=35)

    plt.tight_layout()
    plt.show()


# Example use
plot_result_space(
    x_datapoints=835,
    y_models=5,
    z_experiments=10
)